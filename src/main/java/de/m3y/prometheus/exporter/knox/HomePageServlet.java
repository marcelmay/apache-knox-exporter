package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.util.Arrays;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Displays a welcome page containing build info and link to metrics.
 */
public class HomePageServlet extends HttpServlet {

    private final ConfigLoader configLoader;
    private final BuildInfoExporter buildInfoExporter;

    HomePageServlet(ConfigLoader configLoader, BuildInfoExporter buildInfoExporter) {
        this.configLoader = configLoader;
        this.buildInfoExporter = buildInfoExporter;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Config config = configLoader.getCurrentConfig();
        StringBuilder buf = new StringBuilder().append("<html>\n"
                + "<head><title>Apache Knox Exporter</title></head>\n"
                + "<body>\n"
                + "<h1>Apache Knox Exporter</h1>\n"
                + "<p><a href=\"/metrics\">Metrics</a></p>\n"
                + "<h2>Build info</h2>"
                + "<ul>"
                + "<li>App version: ").append(buildInfoExporter.getAppVersion()).append("</li>"
                + "<li>Build time : ").append(buildInfoExporter.getBuildTimeStamp()).append("</li>"
                + "<li>SCM branch : ").append(buildInfoExporter.getBuildScmBranch()).append("</li>"
                + "<li>SCM version : ").append(buildInfoExporter.getBuildScmVersion()).append("</li>"
                + "</ul>"
                + "<h2>Configuration</h2><ul>"
                + "<li>default username : ").append(config.getDefaultUsername()).append("</li>"
                + "<li>WebHDFS services"
                + "<ul>");
        for (Config.WebHdfsService webHdfsService : config.getWebHdfsServices()) {
            buf.append("<li><ul>")
                    .append("<li>Knox URL : ").append(webHdfsService.getKnoxUrl()).append("</li>")
                    .append("<li>Username (default override) : ").append(getEmtpyStringIfNull(webHdfsService.getUsername())).append("</li>")
                    .append("<li>Status Path : ").append(Arrays.toString(webHdfsService.getStatusPaths())).append("</li>")
                    .append("</ul></li>");
        }
        buf.append("</ul></li>")
                .append("<li>HBase services<ul>");
        for (Config.HBaseService hBaseService : config.getHbaseServices()) {
            buf.append("<li><ul>")
                    .append("<li>Knox URL : ").append(hBaseService.getKnoxUrl()).append("</li>")
                    .append("<li>Username (default override) : ").append(getEmtpyStringIfNull(hBaseService.getUsername())).append("</li>")
                    .append("</ul></li>");
        }
        buf.append("</ul></li>")
                .append("<li>Hive services<ul>");
        for (Config.HiveService hiveService : config.getHiveServices()) {
            buf.append("<li><ul>")
                    .append("<li>JDBC URL : ").append(hiveService.getJdbcUrl()).append("</li>")
                    .append("<li>Username  (default override) : ").append(getEmtpyStringIfNull(hiveService.getUsername())).append("</li>")
                    .append("<li>Queries : ").append(Arrays.toString(hiveService.getQueries())).append("</li>")
                    .append("</ul></li>");
        }
        buf.append("</ul></li></html>");
        resp.setContentType("text/html");
        resp.getWriter().print(buf);
    }

    private String getEmtpyStringIfNull(String value) {
        return null == value ? "" : value;
    }
}