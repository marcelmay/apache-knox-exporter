package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    final Config config;
    final String knoxGatewayUrl;

    KnoxCollector(String knoxGatewayUrl, Config config) {
        this.knoxGatewayUrl = knoxGatewayUrl;
        this.config = config;
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

    private void scrapeKnox() throws URISyntaxException, IOException {
        try (final Hadoop hadoop = Hadoop.login(knoxGatewayUrl, config.getUsername(), config.getPassword())) {
            scrapeKnox(hadoop);
        }
    }

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


    static class CallableBasicResponseAdapter implements Callable<Boolean> {
        private final Callable<? extends BasicResponse> knoxRequest;

        CallableBasicResponseAdapter(Callable<? extends BasicResponse> knoxRequest) {
            this.knoxRequest = knoxRequest;
        }

        @Override
        public Boolean call() throws Exception {
            try (BasicResponse basicResponse = knoxRequest.call()) {
                if (basicResponse.getStatusCode() == 200) {
                    return Boolean.TRUE;
                } else if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Failed knox check, status={}, response={}",
                            basicResponse.getStatusCode(), basicResponse.getString());
                }
            }
            return Boolean.FALSE;
        }
    }

    static class HiveCheck implements Callable<Boolean> {
        static {
            try {
                Class.forName(HiveDriver.class.getName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Can not load hive JDBC driver", e);
            }
        }

        private final Config config;

        HiveCheck(Config config) {
            this.config = config;
        }

        @Override
        public Boolean call() throws Exception {
            try (Connection con = DriverManager.getConnection(config.getHiveJdbcUrl(),
                    config.getUsername(), config.getPassword())) {
                try (Statement stmt = con.createStatement()) {
                    try (ResultSet resultSet = stmt.executeQuery(config.getHiveQuery())) {
                        if (resultSet.next()) {
                            return Boolean.TRUE;
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("No Hive ResultSet.next()");
                        }
                    }
                }

            }
            return Boolean.FALSE;
        }
    }

    private void scrapeKnox(Hadoop hadoop) {
        Map<String, Callable<Boolean>> actions = new HashMap<>();
        if (isNotEmpty(config.getWebHdfStatusPath())) {
            actions.put("webhdfs_status", new CallableBasicResponseAdapter(
                    Hdfs.status(hadoop).file(config.getWebHdfStatusPath()).callable()));
        }

        if (isNotEmpty(config.getHiveJdbcUrl())) {
            actions.put("hive_query", new HiveCheck(config));
        }

        for (Map.Entry<String, Callable<Boolean>> entry : actions.entrySet()) {
            final String action = entry.getKey();
            try {
                try (final Summary.Timer timer = METRIC_OPS_LATENCY.labels(action).startTimer()) {
                    if (Boolean.FALSE.equals(entry.getValue().call())) {
                        KNOX_OPS_ERRORS.labels(action).inc();
                    }
                }
            } catch (Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Received error invoking {} : {}", action, e);
                }
                KNOX_OPS_ERRORS.labels(action).inc();
            }
        }

    }

    private boolean isNotEmpty(String value) {
        return null != value && !value.isEmpty();
    }
}
