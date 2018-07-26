package de.m3y.prometheus.exporter.knox;

/**
 * Config options for collector.
 */
public class Config {
    private String defaultUsername;
    private String defaulPassword;
    private WebHdfsService[] webHdfsServices;
    private HiveService[] hiveServices;

    public abstract static class KnoxService {
        private String name;
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

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class HiveService extends KnoxService {
        private String jdbcUrl;
        private String[] queries;

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

        static HiveService of(String name, String username, String password, String jdbcUrl, String... query) {
            HiveService hiveCheck = new HiveService();
            hiveCheck.setName(name);
            hiveCheck.setUsername(username);
            hiveCheck.setPassword(password);
            hiveCheck.setJdbcUrl(jdbcUrl);
            hiveCheck.setQueries(query);
            return hiveCheck;
        }
    }

    public static class WebHdfsService extends KnoxService {
        String knoxUrl;
        String[] statusPaths;

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

        static WebHdfsService of(String name, String username, String password, String knoxUrl, String... statusPaths) {
            WebHdfsService webHdfsCheck = new WebHdfsService();
            webHdfsCheck.setName(name);
            webHdfsCheck.setUsername(username);
            webHdfsCheck.setPassword(password);
            webHdfsCheck.setKnoxUrl(knoxUrl);
            webHdfsCheck.setStatusPaths(statusPaths);
            return webHdfsCheck;
        }
    }

    public String getDefaultUsername() {
        return defaultUsername;
    }

    public void setDefaultUsername(String defaultUsername) {
        this.defaultUsername = defaultUsername;
    }

    public String getDefaulPassword() {
        return defaulPassword;
    }

    public void setDefaulPassword(String defaulPassword) {
        this.defaulPassword = defaulPassword;
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
}

