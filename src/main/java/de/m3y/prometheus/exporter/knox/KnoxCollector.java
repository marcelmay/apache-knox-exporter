package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import org.apache.hive.jdbc.HiveDriver;
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.Hadoop;
import org.apache.knox.gateway.shell.hdfs.Hdfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects stats for Knox services.
 */
public class KnoxCollector extends Collector {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnoxCollector.class);

    static final String METRIC_PREFIX = "knox_exporter_";

    private static final Counter METRIC_SCRAPE_REQUESTS = Counter.build()
            .name(METRIC_PREFIX + "scrape_requests_total")
            .help("Exporter requests made").register();
    private static final Counter METRIC_SCRAPE_ERROR = Counter.build()
            .name(METRIC_PREFIX + "scrape_errors_total")
            .help("Counts failed scrapes.").register();

    private static final Gauge METRIC_SCRAPE_DURATION = Gauge.build()
            .name(METRIC_PREFIX + "scrape_duration_seconds")
            .help("Scrape duration").register();

    private static final Counter KNOX_OPS_ERRORS = Counter.build()
            .name(METRIC_PREFIX + "ops_errors_total")
            .help("Counts errors.")
            .labelNames("action")
            .register();

    private static final Summary METRIC_OPS_LATENCY = Summary.build()
            .name(METRIC_PREFIX + "ops_duration_seconds")
            .help("Ops duration")
            .labelNames("action")
            .quantile(0.5, 0.05)
            .quantile(0.95, 0.01)
            .quantile(0.99, 0.001)
            .register();
    private static final String HIVE_QUERY = "hive_query";
    private static final String WEBHDFS_STATUS = "webhdfs_status";

    static { // https://www.robustperception.io/existential-issues-with-metrics
        KNOX_OPS_ERRORS.labels(WEBHDFS_STATUS);
        KNOX_OPS_ERRORS.labels(HIVE_QUERY);
    }

    final Config config;
    final ExecutorService executorService;

    KnoxCollector(Config config) {
        this.config = config;
        executorService = Executors.newFixedThreadPool(countServices(config) /* Thread per service check */);
    }

    private int countServices(Config config) {
        int count = 0;
        if (config.getHiveServices() != null) {
            for (Config.HiveService hiveService : config.getHiveServices()) {
                if (hiveService.getQueries() != null) {
                    count += hiveService.getQueries().length;
                }
            }
        }
        if (config.getWebHdfsServices() != null) {
            for (Config.WebHdfsService webHdfsService : config.getWebHdfsServices()) {
                if (webHdfsService.statusPaths != null) {
                    count += webHdfsService.statusPaths.length;
                }
            }
        }
        return count;
    }

    public List<MetricFamilySamples> collect() {
        try (Gauge.Timer timer = METRIC_SCRAPE_DURATION.startTimer()) {
            METRIC_SCRAPE_REQUESTS.inc();

            // Switch report
            synchronized (this) {
                scrapeKnox();
            }
        } catch (Exception e) {
            METRIC_SCRAPE_ERROR.inc();
            LOGGER.error("Scrape failed", e);
        }

        return Collections.emptyList(); // Directly registered counters
    }

    public void shutdown() {
        if (null != executorService) {
            LOGGER.info("Shutting down executor service ...");
            executorService.shutdown();

            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.warn("Executor service failed to await termination of running checks", e);
            }

            // Try to force shutdown
            if (!executorService.isTerminated()) {
                executorService.shutdownNow();
            }
        }
    }

    private void scrapeKnox() throws URISyntaxException, IOException {
        List<Callable<Boolean>> actions = configureActions();

        try {
            executorService.invokeAll(actions,
                    55 /* 60s is usually a default timeout for nginx etc. */, TimeUnit.SECONDS).stream()
                    .forEach((callable) -> {
                        if (!callable.isDone()) { // Terminated -> error
                            METRIC_SCRAPE_ERROR.inc();
                        }
                    });
        } catch (InterruptedException ex) {
            METRIC_SCRAPE_ERROR.inc();
            LOGGER.error("Failed to invoke actions", ex);
        }
    }

    private List<Callable<Boolean>> configureActions() {
        List<Callable<Boolean>> actions = new ArrayList<>();
        for (Config.WebHdfsService webHdfsService : config.getWebHdfsServices()) {
            String password = webHdfsService.getPassword();
            if (null == password) {
                password = config.getDefaulPassword();
            }
            String username = webHdfsService.getUsername();
            if (null == username) {
                username = config.getDefaultUsername();
            }
            for (String statusPath : webHdfsService.getStatusPaths()) {
                actions.add(new WebHdfsStatusAction(webHdfsService.getName(), webHdfsService.getKnoxUrl(), statusPath, username, password));
            }
        }
        for (Config.HiveService hiveService : config.getHiveServices()) {
            String password = hiveService.getPassword();
            if (null == password) {
                password = config.getDefaulPassword();
            }
            String username = hiveService.getUsername();
            if (null == username) {
                username = config.getDefaultUsername();
            }
            for (String query : hiveService.getQueries()) {
                actions.add(new HiveQueryAction(hiveService.getName(), hiveService.getJdbcUrl(), query, username, password));
            }
        }
        return actions;
    }


    static abstract class MetricAction implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            final String[] labels = getLabels();
            try (final Summary.Timer timer = METRIC_OPS_LATENCY.labels(labels).startTimer()) {
                if (Boolean.FALSE.equals(perform())) {
                    KNOX_OPS_ERRORS.labels(labels).inc();
                }
            } catch (Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Received error invoking {} : {}", labels, e);
                }
                KNOX_OPS_ERRORS.labels(labels).inc();
            }
            return Boolean.FALSE;
        }

        abstract Boolean perform() throws Exception;

        abstract String[] getLabels();
    }

    static class WebHdfsStatusAction extends MetricAction {
        private final String name;
        private final String knoxUrl;
        private final String username;
        private final String password;
        private final String statusPath;

        WebHdfsStatusAction(String name,String knoxUrl, String statusPath, String username, String password) {
            this.name = name;
            this.knoxUrl = knoxUrl;
            this.username = username;
            this.password = password;
            this.statusPath = statusPath;
        }

        @Override
        Boolean perform() throws Exception {
            try (final Hadoop hadoop = Hadoop.login(knoxUrl, username, password)) {
                try (BasicResponse basicResponse = Hdfs.status(hadoop).file(statusPath).now()) {
                    if (basicResponse.getStatusCode() == 200) {
                        return Boolean.TRUE;
                    } else if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Failed knox check {}, status={}, response={}", name,
                                basicResponse.getStatusCode(), basicResponse.getString());
                    }
                }
            }
            return Boolean.FALSE;
        }

        @Override
        String[] getLabels() {
            return new String[]{name};
        }
    }

    static class HiveQueryAction extends MetricAction {
        static {
            try {
                Class.forName(HiveDriver.class.getName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Can not load hive JDBC driver", e);
            }
        }

        private final String name;
        private final String jdbcUrl;
        private final String query;
        private final String username;
        private final String password;


        HiveQueryAction(String name, String jdbcUrl, String query, String username, String password) {
            this.name = name;
            this.jdbcUrl = jdbcUrl;
            this.query = query;
            this.username = username;
            this.password = password;
        }

        @Override
        Boolean perform() throws Exception {
            try (Connection con = DriverManager.getConnection(jdbcUrl, username, password)) {
                try (Statement stmt = con.createStatement()) {
                    try (ResultSet resultSet = stmt.executeQuery(query)) {
                        if (resultSet.next()) {
                            return Boolean.TRUE;
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("No Hive ResultSet.next() for {} and query {}", name, query);
                        }
                    }
                }

            }
            return Boolean.FALSE;
        }

        @Override
        String[] getLabels() {
            return new String[]{name};
        }
    }
}
