package de.m3y.prometheus.exporter.knox;

/**
 * Config options for collector.
 */
public class Config {
    private static final int DEFAULT_TIMEOUT_MS = 60000;
    private int timeout = DEFAULT_TIMEOUT_MS;
    private String defaultUsername;
    private String defaultPassword;
    private WebHdfsService[] webHdfsServices = new WebHdfsService[]{};
    private HiveService[] hiveServices = new HiveService[]{};
    private HBaseService[] hbaseServices = new HBaseService[]{};
    private int jdbcLoginTimeout;

    public abstract static class KnoxService {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class HBaseService extends KnoxService {
        private String knoxUrl;

        public String getKnoxUrl() {
            return knoxUrl;
        }

        public void setKnoxUrl(String knoxUrl) {
            this.knoxUrl = knoxUrl;
        }
    }

    public static class HiveService extends KnoxService {
        private String jdbcUrl;
        private String[] queries = new String[]{};

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String[] getQueries() {
            return queries;
        }

        public void setQueries(String[] queries) {
            this.queries = queries;
        }

        static HiveService of(String username, String password, String jdbcUrl, String... query) {
            HiveService hiveCheck = new HiveService();
            hiveCheck.setUsername(username);
            hiveCheck.setPassword(password);
            hiveCheck.setJdbcUrl(jdbcUrl);
            hiveCheck.setQueries(query);
            return hiveCheck;
        }
    }

    public static class WebHdfsService extends KnoxService {
        String knoxUrl;
        String[] statusPaths = new String[]{};

        public String getKnoxUrl() {
            return knoxUrl;
        }

        public void setKnoxUrl(String knoxUrl) {
            this.knoxUrl = knoxUrl;
        }

        public String[] getStatusPaths() {
            return statusPaths;
        }

        public void setStatusPaths(String[] statusPaths) {
            this.statusPaths = statusPaths;
        }

        static WebHdfsService of(String username, String password, String knoxUrl, String... statusPaths) {
            WebHdfsService webHdfsCheck = new WebHdfsService();
            webHdfsCheck.setUsername(username);
            webHdfsCheck.setPassword(password);
            webHdfsCheck.setKnoxUrl(knoxUrl);
            webHdfsCheck.setStatusPaths(statusPaths);
            return webHdfsCheck;
        }
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public void setDefaultUsername(String defaultUsername) {
        this.defaultUsername = defaultUsername;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public void setDefaultPassword(String defaultPassword) {
        this.defaultPassword = defaultPassword;
    }

    public WebHdfsService[] getWebHdfsServices() {
        return webHdfsServices;
    }

    public void setWebHdfsServices(WebHdfsService[] webHdfsServices) {
        this.webHdfsServices = webHdfsServices;
    }

    public HiveService[] getHiveServices() {
        return hiveServices;
    }

    public void setHiveServices(HiveService[] hiveServices) {
        this.hiveServices = hiveServices;
    }

    public HBaseService[] getHbaseServices() {
        return hbaseServices;
    }

    public void setHbaseServices(HBaseService[] hbaseServices) {
        this.hbaseServices = hbaseServices;
    }

    public int getJdbcLoginTimeout() {
        return jdbcLoginTimeout;
    }

    public void setJdbcLoginTimeout(int jdbcLoginTimeout) {
        this.jdbcLoginTimeout = jdbcLoginTimeout;
    }
}

