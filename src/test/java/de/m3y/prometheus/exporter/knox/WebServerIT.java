package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

public class WebServerIT {
    private static WebServer webServer;
    private static String exporterBaseUrl;
    private static OkHttpClient client;

    @BeforeClass
    public static void setUp() throws Exception {
        Config config;
        try (Reader reader = new InputStreamReader(
                Thread.currentThread().getContextClassLoader().getResourceAsStream("config-it.yml"))) {
            config = new Yaml().loadAs(reader, Config.class);
        }

        webServer = new WebServer().configure(config, "localhost", 7772,
                "http://localhost:8080/gateway/default");
        webServer.start();
        exporterBaseUrl = "http://localhost:7772";
        client = new OkHttpClient();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (null != webServer) {
            webServer.shutdown();
        }
    }

    @Test
    public void testMetrics() throws Exception {
        Response response = getResponse(exporterBaseUrl + "/metrics");
        assertThat(response.code()).isEqualTo(200);

        String body = response.body().string();

        // App info
        assertThat(body).contains("knox_exporter_scrape_duration_seconds");
        assertThat(body).contains("knox_exporter_app_info{appName=\"knox_exporter\",appVersion=");
        assertThat(body).contains("knox_exporter_scrape_errors_total 0.0");
        assertThat(body).contains("knox_exporter_scrape_requests_total");

        // JVM
        assertThat(body).contains("jvm_memory_bytes_used{area=\"heap\",}");
        assertThat(body).contains("jvm_memory_bytes_used{area=\"nonheap\",}");
        assertThat(body).contains("jvm_memory_bytes_max{area=\"heap\",}");
        assertThat(body).contains("jvm_memory_pool_bytes_used");
        assertThat(body).contains("jvm_memory_pool_bytes_committed");
        assertThat(body).contains("jvm_memory_pool_bytes_max");


        // knox_exporter_ops_duration_seconds
        // WebHDFS
        assertThat(body).contains("knox_exporter_ops_duration_seconds{action=\"webhdfs_status\",quantile=\"0.5\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds{action=\"webhdfs_status\",quantile=\"0.95\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds{action=\"webhdfs_status\",quantile=\"0.99\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds_count{action=\"webhdfs_status\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds_sum{action=\"webhdfs_status\",}");
        // Hive
        assertThat(body).contains("knox_exporter_ops_duration_seconds{action=\"hive_query\",quantile=\"0.5\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds{action=\"hive_query\",quantile=\"0.95\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds{action=\"hive_query\",quantile=\"0.99\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds_count{action=\"hive_query\",}");
        assertThat(body).contains("knox_exporter_ops_duration_seconds_sum{action=\"hive_query\",}");

        // knox_exporter_ops_errors_total
        assertThat(body).contains("knox_exporter_ops_errors_total{action=\"webhdfs_status\",}");
    }

    @Test
    public void testWelcomePage() throws Exception {
        Response response = getResponse(exporterBaseUrl + "/");
        assertThat(response.code()).isEqualTo(200);

        String body = response.body().string();
        assertThat(body).contains("Apache Knox Exporter");

        // Build info
        assertThat(body).contains("App version:");
        assertThat(body).contains("Build time :");
        assertThat(body).contains("SCM branch :");
        assertThat(body).contains("SCM version :");

        // Config options
        assertThat(body).contains("Knox gateway URL : http://localhost:8080/gateway/default");
        assertThat(body).contains("username : foo");
        assertThat(body).contains("webHdfStatusPath : /");
    }

    private Response getResponse(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return client.newCall(request).execute();
    }
}
