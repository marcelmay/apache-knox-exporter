package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import org.apache.hive.jdbc.HiveDriver;
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.Hadoop;
import org.apache.knox.gateway.shell.hbase.HBase;
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
            .labelNames("action", "uri", "user", "param")
            .register();

    private static final Summary METRIC_OPS_LATENCY = Summary.build()
            .name(METRIC_PREFIX + "ops_duration_seconds")
            .help("Ops duration")
            .labelNames("action", "uri", "user", "param")
            .quantile(0.5, 0.05)
            .quantile(0.95, 0.01)
            .quantile(0.99, 0.001)
            .register();
    private static final String ACTION_HIVE_QUERY = "hive_query";
    private static final String ACTION_WEBHDFS_STATUS = "webhdfs_status";
    private static final String ACTION_HBASE_STATUS = "hbase_status";

    private final ConfigLoader configLoader;
    private final ThreadPoolExecutor executorService;
    private List<Callable<Boolean>> actions;
    private Config config;

    KnoxCollector(ConfigLoader configLoader) {
        this.configLoader = configLoader;

        config = configLoader.getOrLoadIfModified();
        actions = configureActions(config);
        executorService = new ThreadPoolExecutor(actions.size(), actions.size(),
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    public synchronized List<MetricFamilySamples> collect() {
        try (Gauge.Timer timer = METRIC_SCRAPE_DURATION.startTimer()) {
            METRIC_SCRAPE_REQUESTS.inc();

            scrapeKnox();
        } catch (Exception e) {
            METRIC_SCRAPE_ERROR.inc();
            LOGGER.error("Scrape failed", e);
        }

        return Collections.emptyList(); // Directly registered counters
    }

    synchronized void shutdown() {
        final int timeout = 5;
        if (null != executorService) {
            LOGGER.info("Shutting down executor service ...");
            executorService.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!executorService.awaitTermination(timeout, TimeUnit.SECONDS)) {
                    executorService.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!executorService.awaitTermination(timeout, TimeUnit.SECONDS))
                        LOGGER.error("Pool did not terminate after a timeout of {}s", timeout);
                }
            } catch (InterruptedException ie) {
                LOGGER.warn("Executor service failed to await termination of running tasks", ie);
                // (Re-)Cancel if current thread also interrupted
                executorService.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }

    private void scrapeKnox() {
        if (configLoader.hasModifications()) {
            config = configLoader.getOrLoadIfModified();
            actions = configureActions(config);
            if (actions.size() != executorService.getMaximumPoolSize()) {
                executorService.setMaximumPoolSize(actions.size());
                executorService.setCorePoolSize(actions.size());
            }
            LOGGER.info("Reloaded config.");
        }

        try {
            executorService.invokeAll(actions,
                    55 /* 60s is usually a default timeout for nginx etc. */, TimeUnit.SECONDS)
                    .forEach(callable -> {
                        if (!callable.isDone()) { // Terminated -> error
                            METRIC_SCRAPE_ERROR.inc();
                        }
                    });
        } catch (InterruptedException ex) {
            METRIC_SCRAPE_ERROR.inc();
            LOGGER.error("Failed to invoke actions", ex);
            Thread.currentThread().interrupt();
        }
    }

    private List<Callable<Boolean>> configureActions(Config config) {
        List<Callable<Boolean>> newActions = new ArrayList<>();

        for (Config.WebHdfsService webHdfsService : config.getWebHdfsServices()) {
            for (String statusPath : webHdfsService.getStatusPaths()) {
                final WebHdfsStatusAction webHdfsStatusAction = new WebHdfsStatusAction(webHdfsService.getKnoxUrl(),
                        statusPath,
                        handleDefaultValue(webHdfsService.getUsername(), config.getDefaultUsername()),
                        handleDefaultValue(webHdfsService.getPassword(), config.getDefaultPassword()));
                newActions.add(webHdfsStatusAction);

                // https://www.robustperception.io/existential-issues-with-metrics
                KNOX_OPS_ERRORS.labels(webHdfsStatusAction.getLabels());
            }
        }

        for (Config.HiveService hiveService : config.getHiveServices()) {
            for (String query : hiveService.getQueries()) {
                final HiveQueryAction hiveQueryAction = new HiveQueryAction(hiveService.getJdbcUrl(),
                        query,
                        handleDefaultValue(hiveService.getUsername(), config.getDefaultUsername()),
                        handleDefaultValue(hiveService.getPassword(), config.getDefaultPassword()));
                newActions.add(hiveQueryAction);

                // https://www.robustperception.io/existential-issues-with-metrics
                KNOX_OPS_ERRORS.labels(hiveQueryAction.getLabels());
            }
        }

        for (Config.HBaseService hBaseService : config.getHbaseServices()) {
            final HbaseStatusAction hbaseStatusAction = new HbaseStatusAction(hBaseService.getKnoxUrl(),
                    handleDefaultValue(hBaseService.getUsername(), config.getDefaultUsername()),
                    handleDefaultValue(hBaseService.getPassword(), config.getDefaultPassword()));
            newActions.add(hbaseStatusAction);

            // https://www.robustperception.io/existential-issues-with-metrics
            KNOX_OPS_ERRORS.labels(hbaseStatusAction.getLabels());
        }

        return newActions;
    }

    private String handleDefaultValue(String value, String defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        return value;
    }

    abstract static class MetricAction implements Callable<Boolean> {
        @Override
        public Boolean call() {
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

        abstract Boolean perform();

        abstract String[] getLabels();
    }

    static class HbaseStatusAction extends MetricAction {
        private final String knoxUrl;
        private final String username;
        private final String password;
        private final String[] labels;

        HbaseStatusAction(String knoxUrl, String username, String password) {
            this.knoxUrl = knoxUrl;
            this.username = username;
            this.password = password;
            labels = new String[]{ACTION_HBASE_STATUS, knoxUrl, username, "-"};
        }

        @Override
        Boolean perform() {
            try (final Hadoop hadoop = Hadoop.login(knoxUrl, username, password)) {
                try (BasicResponse basicResponse = HBase.session(hadoop).status().now()) {
                    if (basicResponse.getStatusCode() == 200) {
                        return Boolean.TRUE;
                    } else if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Failed HBase status action using knox url {}. " +
                                        " Response status code is {}, body is {}",
                                knoxUrl,
                                basicResponse.getStatusCode(), basicResponse.getString());
                    }
                }
            } catch (IOException | URISyntaxException e) {
                LOGGER.warn("Failed to perform HBase status action: {}", e.getMessage());
            }
            return Boolean.FALSE;
        }

        @Override
        String[] getLabels() {
            return labels;
        }
    }

    static class WebHdfsStatusAction extends MetricAction {
        private final String knoxUrl;
        private final String username;
        private final String password;
        private final String statusPath;
        private final String[] labels;

        WebHdfsStatusAction(String knoxUrl, String statusPath, String username, String password) {
            this.knoxUrl = knoxUrl;
            this.username = username;
            this.password = password;
            this.statusPath = statusPath;
            labels = new String[]{ACTION_WEBHDFS_STATUS, knoxUrl, username, statusPath};
        }

        @Override
        Boolean perform() {
            try (final Hadoop hadoop = Hadoop.login(knoxUrl, username, password)) {
                try (BasicResponse basicResponse = Hdfs.status(hadoop).file(statusPath).now()) {
                    if (basicResponse.getStatusCode() == 200) {
                        return Boolean.TRUE;
                    } else if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Failed WebHDFS check using knox url {} and webhdf status for path {}." +
                                        " Response status code is {}, body is {}",
                                knoxUrl, statusPath,
                                basicResponse.getStatusCode(), basicResponse.getString());
                    }
                }
            } catch (IOException | URISyntaxException e) {
                LOGGER.warn("Failed to perform WebHDFS status action: {}", e.getMessage());
            }
            return Boolean.FALSE;
        }

        @Override
        String[] getLabels() {
            return labels;
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

        private final String jdbcUrl;
        private final String query;
        private final String username;
        private final String password;
        private final String[] labels;


        HiveQueryAction(String jdbcUrl, String query, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.query = query;
            this.username = username;
            this.password = password;
            labels = new String[]{ACTION_HIVE_QUERY,
                    jdbcUrl.replaceAll("trustStorePassword=.*?;", ""), // Filter out security critical info
                    username, query};
        }

        @Override
        Boolean perform() {
            try (Connection con = DriverManager.getConnection(jdbcUrl, username, password)) {
                try (Statement stmt = con.createStatement()) {
                    try (ResultSet resultSet = stmt.executeQuery(query)) {
                        if (resultSet.next()) {
                            return Boolean.TRUE;
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("No Hive ResultSet.next() to {} using query {} and user {}",
                                    jdbcUrl, query, username);
                        }
                    }
                }

            } catch (SQLException e) {
                LOGGER.warn("Could not perform jdbc action : {}", e.getMessage());
            }
            return Boolean.FALSE;
        }

        @Override
        String[] getLabels() {
            return labels;
        }
    }
}
