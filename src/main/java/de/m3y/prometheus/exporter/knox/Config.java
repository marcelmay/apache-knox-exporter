package de.m3y.prometheus.exporter.knox;

/**
 * Config options for collector.
 */
public class Config {
    private String username;
    private String password;

    private String webHdfStatusPath;

    /**
     * The knox user for connecting.
     *
     * @return the user name.
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * The knox user password for connecting.
     *
     * @return the user password.
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * The WebHDFS status path of a file or directory, eg '/'
     */
    public String getWebHdfStatusPath() {
        return webHdfStatusPath;
    }

    public void setWebHdfStatusPath(String webHdfStatusPath) {
        this.webHdfStatusPath = webHdfStatusPath;
    }
}

