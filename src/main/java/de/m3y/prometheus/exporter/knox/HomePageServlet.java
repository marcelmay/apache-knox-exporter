package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Displays a welcome page containing build info and link to metrics.
 */
public class HomePageServlet extends HttpServlet {

    private final Config config;
    private final BuildInfoExporter buildInfoExporter;
    private final String knoxGatewayUrl;

    HomePageServlet(String knoxGatewayUrl, Config config, BuildInfoExporter buildInfoExporter) {
        this.knoxGatewayUrl = knoxGatewayUrl;
        this.config = config;
        this.buildInfoExporter = buildInfoExporter;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
                + "<li>Knox gateway URL : ").append(knoxGatewayUrl).append("</li>"
                + "<li>username : ").append(config.getUsername()).append("</li>"
                + "<li>webHdfStatusPath : ").append(config.getWebHdfStatusPath()).append("</li>"
                + "<li>hiveJdbcUrl : ").append(config.getHiveJdbcUrl()).append("</li>"
                + "<li>hiveQuery : ").append(config.getHiveQuery()).append("</li>"
                + "</ul></body>\n"
                + "</html>");
        resp.setContentType("text/html");
        resp.getWriter().print(buf);
    }
}