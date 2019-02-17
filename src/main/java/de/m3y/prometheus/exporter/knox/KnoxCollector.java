package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import org.apache.hive.jdbc.HiveDriver;
import org.apache.knox.gateway.shell.*;
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
            .help("Ops error counts.")
            .labelNames("action", "uri", "user", "param", "status")
            .register();

    private static final Summary KNOX_OPS_DURATION = Summary.build()
            .name(METRIC_PREFIX + "ops_duration_seconds")
            .help("Duration of successful and failed operations")
            .labelNames("action", "uri", "user", "param", "status")
            .quantile(0.5, 0.05)
            .quantile(0.95, 0.01)
            .quantile(0.99, 0.001)
            .register();
    private static final String ACTION_HIVE_QUERY = "hive_query";
    private static final String ACTION_WEBHDFS_STATUS = "webhdfs_status";
    private static final String ACTION_HBASE_STATUS = "hbase_status";

    private final ConfigLoader configLoader;
    private final ThreadPoolExecutor executorService;

    KnoxCollector(ConfigLoader configLoader) {
        this.configLoader = configLoader;

        // Initialize with a default size. Will be later reconfigured depending on
        // exporter configuration.
        executorService = new CustomExecutor();
    }

    public synchronized List<MetricFamilySamples> collect() {
        try (Gauge.Timer timer = METRIC_SCRAPE_DURATION.startTimer()) {
            METRIC_SCRAPE_REQUESTS.inc();

            scrapeKnox(reloadConfigIfRequired());
        } catch (Exception e) {
            METRIC_SCRAPE_ERROR.inc();
            LOGGER.error("Scrape failed", e);
        }

        return Collections.emptyList(); // Directly registered counters
    }

    synchronized void shutdown() {
        final int timeout = 5;
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

    private void scrapeKnox(Config config) {
        final List<AbstractBaseAction> actions = configureActions(config);

        // Resize pool?
        if (actions.size() != executorService.getMaximumPoolSize()) {
            executorService.setCorePoolSize(actions.size());
            executorService.setMaximumPoolSize(actions.size());
        }

        try {
            final int timeout = config.getTimeout();
            List<Future<Boolean>> futures = executorService.invokeAll(actions, timeout, TimeUnit.MILLISECONDS);
            updateMetrics(futures, actions);
        } catch (InterruptedException ex) {
            METRIC_SCRAPE_ERROR.inc();
            LOGGER.error("Failed to invoke actions", ex);
            Thread.currentThread().interrupt();
        }
    }

    private void updateMetrics(List<Future<Boolean>> futures, List<AbstractBaseAction> actions) {
        for (int i = 0; i < futures.size(); i++) {
            final Future<Boolean> future = futures.get(i);
            final AbstractBaseAction action = actions.get(i);
            updateMetrics(future, action);
        }
    }

    private void updateMetrics(Future<Boolean> future, AbstractBaseAction action) {
        if (future instanceof CustomExecutor.TimedFutureTask) {
            // convert ns to seconds
            double durationSeconds = ((CustomExecutor.TimedFutureTask) future).getDurationNs() / 1000.0 / 1000.0 / 1000.0;

            if (future.isCancelled()) {
                // Timed out => ops error
                KNOX_OPS_ERRORS.labels(action.getLabels()).inc();
                KNOX_OPS_DURATION.labels(action.getLabels())
                        .observe(durationSeconds);
            } else {
                try {
                    Boolean result = future.get();
                    KNOX_OPS_DURATION.labels(action.getLabels())
                            .observe(durationSeconds);
                    if (!result) {
                        // Not OK => ops error
                        KNOX_OPS_ERRORS.labels(action.getLabels()).inc();
                    }
                } catch (ExecutionException | InterruptedException | CancellationException e) {
                    // Should not happen ...
                    LOGGER.error("Can not get result for action " + action, e);
                    KNOX_OPS_ERRORS.labels(action.getLabels()).inc();
                    KNOX_OPS_DURATION.labels(action.getLabels()).observe(durationSeconds);
                    METRIC_SCRAPE_ERROR.inc();

                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } else {
            LOGGER.warn("Unexpected future {}, expected TimedFutureTask", future.getClass());
        }
    }

    private Config reloadConfigIfRequired() {
        if (configLoader.hasModifications()) {
            configLoader.getOrLoadIfModified();
            LOGGER.info("Reloaded and reconfigured.");
        }
        return configLoader.getCurrentConfig();
    }

    private List<AbstractBaseAction> configureActions(Config config) {
        List<AbstractBaseAction> newActions = new ArrayList<>();

        for (Config.WebHdfsService webHdfsService : config.getWebHdfsServices()) {
            for (String statusPath : webHdfsService.getStatusPaths()) {
                final WebHdfsStatusAction webHdfsStatusAction = new WebHdfsStatusAction(webHdfsService.getKnoxUrl(),
                        statusPath,
                        handleDefaultValue(webHdfsService.getUsername(), config.getDefaultUsername()),
                        handleDefaultValue(webHdfsService.getPassword(), config.getDefaultPassword()),
                        config.getTimeout());
                newActions.add(webHdfsStatusAction);
            }
        }

        final Config.HiveService[] hiveServices = config.getHiveServices();
        if (null != hiveServices && hiveServices.length > 0) {
            initHiveDriver(config.getJdbcLoginTimeout());
            for (Config.HiveService hiveService : hiveServices) {
                for (String query : hiveService.getQueries()) {
                    final HiveQueryAction hiveQueryAction = new HiveQueryAction(hiveService.getJdbcUrl(),
                            query,
                            handleDefaultValue(hiveService.getUsername(), config.getDefaultUsername()),
                            handleDefaultValue(hiveService.getPassword(), config.getDefaultPassword()));
                    newActions.add(hiveQueryAction);
                }
            }
        }

        for (Config.HBaseService hBaseService : config.getHbaseServices()) {
            final HbaseStatusAction hbaseStatusAction = new HbaseStatusAction(hBaseService.getKnoxUrl(),
                    handleDefaultValue(hBaseService.getUsername(), config.getDefaultUsername()),
                    handleDefaultValue(hBaseService.getPassword(), config.getDefaultPassword()),
                    config.getTimeout());
            newActions.add(hbaseStatusAction);
        }

        return newActions;
    }

    private String handleDefaultValue(String value, String defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        return value;
    }

    abstract static class AbstractBaseAction implements CustomExecutor.CancellableCallable<Boolean> {
        enum Status {
            UNKNOWN,
            SUCCESS,
            ERROR_AUTH,
            ERROR_TIMEOUT,
            ERROR_OTHER,
        }

        @Override
        public Boolean call() {
            return perform();
        }

        abstract boolean perform();

        abstract String[] getLabels();
    }

    abstract class AbstractKnoxBaseAction extends AbstractBaseAction {
        protected final String knoxUrl;
        private final String[] labels;
        protected final ClientContext clientContext;
        protected KnoxSession knoxSession;

        AbstractKnoxBaseAction(String action, String knoxUrl, String username, String password, String param, int timeout) {
            super();
            this.knoxUrl = knoxUrl;
            labels = new String[]{action, knoxUrl, username, param, Status.UNKNOWN.name()};
            clientContext = ClientContext.with(username, password, knoxUrl);
            final ClientContext.SocketContext socketContext = clientContext.socket();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Setting timeout to {}ms for {}", timeout, Arrays.toString(getLabels()));
            }
            socketContext.timeout(timeout);
            socketContext.reuseAddress(true);
        }

        protected void setLabelStatus(Status status) {
            if (Status.UNKNOWN.name().equals(labels[4])) {
                labels[4] = status.name();
            } else {
                LOGGER.warn("Ignoring update for status label {} to {}", labels[4], status);
            }
        }

        @Override
        public RunnableFuture<Boolean> newTask() {
            return new CustomExecutor.TimedFutureTask<Boolean>(this) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    try {
                        AbstractKnoxBaseAction.this.cancel();
                    } catch (Exception ex) {
                        LOGGER.debug("Ignoring exception when cancelling", ex);
                    }
                    return super.cancel(mayInterruptIfRunning);
                }

                @Override
                public String toString() {
                    return AbstractKnoxBaseAction.this.getClass().getName() + "-"
                            + Arrays.toString(AbstractKnoxBaseAction.this.getLabels());
                }
            };
        }

        @Override
        boolean perform() {
            try (KnoxSession tmpHadoop = new KnoxSession(clientContext)) {
                this.knoxSession = tmpHadoop; // Track for cancelling the action.
                final AbstractRequest<? extends BasicResponse> request = createRequest(knoxSession);
                try (BasicResponse basicResponse = request.now()) {
                    if (basicResponse.getStatusCode() == 200) {
                        setLabelStatus(Status.SUCCESS);
                        return true;
                    }

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Failed knox action using knox url {}. " +
                                        " Response status code is {}, body is {}",
                                knoxUrl,
                                basicResponse.getStatusCode(), basicResponse.getString());
                    }
                    if (basicResponse.getStatusCode() == 401) {
                        setLabelStatus(Status.ERROR_AUTH);
                    } else {
                        setLabelStatus(Status.ERROR_OTHER);
                    }
                }
            } catch (KnoxShellException e) {
                // Trying to compensate error handling with heuristic
                if (e.getMessage().contains("HTTP/1.1 401 Unauthorized" /* TODO: Pattern+1.x? */)) {
                    setLabelStatus(Status.ERROR_AUTH);
                } else {
                    setLabelStatus(Status.ERROR_OTHER);
                }
                LOGGER.warn("Failed to perform knox action {} : {}", Arrays.toString(labels), e.getMessage());
            } catch (IOException | URISyntaxException e) {
                setLabelStatus(Status.ERROR_OTHER);
                LOGGER.warn("Failed to perform knox action {} : {}", Arrays.toString(labels), e.getMessage());
            }
            return false;
        }

        protected abstract AbstractRequest<? extends BasicResponse> createRequest(KnoxSession knoxSession); //NOSONAR

        @Override
        public void cancel() {
            setLabelStatus(Status.ERROR_TIMEOUT);
            if (null != knoxSession) {
                try {
                    knoxSession.close();
                } catch (IOException e) {
                    LOGGER.warn("Failed to close knox session. Ignored for cancelling.", e);
                }
            }
        }

        @Override
        String[] getLabels() {
            return labels;
        }
    }

    class HbaseStatusAction extends AbstractKnoxBaseAction {
        HbaseStatusAction(String knoxUrl, String username, String password, int timeout) {
            super(ACTION_HBASE_STATUS, knoxUrl, username, password, "-", timeout);
        }

        protected AbstractRequest<? extends BasicResponse> createRequest(KnoxSession knoxSession) {
            return HBase.session(knoxSession).status();
        }
    }

    class WebHdfsStatusAction extends AbstractKnoxBaseAction {
        private final String statusPath;

        WebHdfsStatusAction(String knoxUrl, String statusPath, String username, String password, int timeout) {
            super(ACTION_WEBHDFS_STATUS, knoxUrl, username, password, statusPath, timeout);
            this.statusPath = statusPath;
        }

        @Override
        protected AbstractRequest<? extends BasicResponse> createRequest(KnoxSession knoxSession) {
            return Hdfs.status(knoxSession).file(statusPath);
        }
    }

    static void initHiveDriver(int jdbcLoginTimeout) {
        if (jdbcLoginTimeout > 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Setting JDBC driver login timeout to {}s", jdbcLoginTimeout);
            }
            DriverManager.setLoginTimeout(jdbcLoginTimeout);
        }
        try {
            Class.forName(HiveDriver.class.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Can not load hive JDBC driver", e);
        }
    }

    class HiveQueryAction extends AbstractBaseAction {
        private final String jdbcUrl;
        private final String query;
        private final String username;
        private final String password;
        private final String[] labels;
        private Connection con;

        @Override
        public RunnableFuture<Boolean> newTask() {
            return new CustomExecutor.TimedFutureTask<Boolean>(this) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    try {
                        HiveQueryAction.this.cancel();
                    } catch (Exception ex) {
                        LOGGER.debug("Ignoring exception when cancelling", ex);
                    }
                    return super.cancel(mayInterruptIfRunning);
                }

                @Override
                public String toString() {
                    return HiveQueryAction.this.getClass().getName() + "-"
                            + Arrays.toString(HiveQueryAction.this.getLabels());
                }
            };
        }

        HiveQueryAction(String jdbcUrl, String query, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.query = query;
            this.username = username;
            this.password = password;
            labels = new String[]{ACTION_HIVE_QUERY,
                    // Filter out security critical info
                    Config.HiveService.escapeJdbcUrl(jdbcUrl),
                    username, query, Status.UNKNOWN.name()};
        }

        @Override
        boolean perform() {
            try (Connection tmpCon = DriverManager.getConnection(jdbcUrl, username, password)) {
                this.con = tmpCon;
                try (Statement stmt = con.createStatement()) {
                    try (ResultSet resultSet = stmt.executeQuery(query)) {
                        if (resultSet.next()) {
                            setLabelStatus(Status.SUCCESS);
                            return true;
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("No Hive ResultSet.next() to {} using query {} and user {}",
                                    jdbcUrl, query, username);
                        }
                        setLabelStatus(Status.ERROR_OTHER);
                    }
                }
            } catch (SQLException | NoClassDefFoundError e) {
                LOGGER.debug("Exception while doing JDBC query {}", query, e);
                LOGGER.warn("Could not perform jdbc action : {}", e.getMessage());
                // Trying to compensate error handling with heuristic
                if (e.getMessage().contains("HTTP Response code: 401")) {
                    setLabelStatus(Status.ERROR_AUTH);
                } else {
                    setLabelStatus(Status.ERROR_OTHER);
                }
            }
            return false;
        }

        @Override
        public void cancel() {
            setLabelStatus(Status.ERROR_TIMEOUT);
            if (null != con) {
                try {
                    con.close();
                } catch (SQLException e) {
                    LOGGER.warn("Failed to close connection. Ignoring.", e);
                }
            }
        }

        protected void setLabelStatus(Status status) {
            if (Status.UNKNOWN.name().equals(labels[4])) {
                labels[4] = status.name();
            } else {
                LOGGER.warn("Ignoring update for status label {} to {}", labels[4], status);
            }
        }

        @Override
        String[] getLabels() {
            return labels;
        }
    }

}
