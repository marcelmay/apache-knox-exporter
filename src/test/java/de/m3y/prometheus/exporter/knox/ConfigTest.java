package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigTest {
    @Test
    public void testParse() throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("config-test.yml")) {
            Config config = new Yaml().loadAs(is, Config.class);

            assertThat(config.getDefaulPassword()).isEqualTo("***");
            assertThat(config.getDefaultUsername()).isEqualTo("foo");

            Config.HiveService[] hiveServices = config.getHiveServices();
            assertThat(hiveServices.length).isEqualTo(2);
            assertThat(hiveServices[0].getUsername()).isEqualTo("foo-2");
            assertThat(hiveServices[0].getPassword()).isEqualTo("****");
            assertThat(hiveServices[0].getJdbcUrl()).isEqualTo("jdbc:hive://knox-hive-server:10000/default");
            assertThat(hiveServices[0].getQueries()).isEqualTo(
                    new String[]{"SELECT current_database()", "something more complex"});

            assertThat(hiveServices[1].getJdbcUrl()).isEqualTo("jdbc:hive://knox-hive-server:10000/other-database");
            assertThat(hiveServices[1].getQueries()).isEqualTo(new String[]{"SELECT current_database()"});

            Config.WebHdfsService[] webHdfsServices = config.getWebHdfsServices();
            assertThat(webHdfsServices.length).isEqualTo(2);
            assertThat(webHdfsServices[0].getKnoxUrl()).isEqualTo("https://my-knox-server/gateway/default");
            assertThat(webHdfsServices[0].getStatusPaths()).isEqualTo(new String[]{"/", "/datalake"});
            assertThat(webHdfsServices[1].getKnoxUrl()).isEqualTo("https://my-knox-server/gateway/another-cluster");
            assertThat(webHdfsServices[1].getStatusPaths()).isEqualTo(new String[]{"/"});
        }
    }
}
