package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigTest {

    static final String CONFIG_TEST_YML = "config-test.yml";

    @Test
    public void testParse() throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CONFIG_TEST_YML)) {
            Config config = new Yaml().loadAs(is, Config.class);

            validateConfig(config);
        }
    }

    static void validateConfig(Config config) {

        assertThat(config.getDefaultPassword()).isEqualTo("***");
        assertThat(config.getDefaultUsername()).isEqualTo("foo");
        assertThat(config.getTimeout()).isEqualTo(59);
        assertThat(config.getJdbcLoginTimeout()).isEqualTo(10);

        Config.HiveService[] hiveServices = config.getHiveServices();
        assertThat(hiveServices.length).isEqualTo(2);
        assertThat(hiveServices[0].getUsername()).isEqualTo("foo-2");
        assertThat(hiveServices[0].getPassword()).isEqualTo("****");
        assertThat(hiveServices[0].getJdbcUrl()).isEqualTo("jdbc:hive2://knox-hive-server:10000/default");
        assertThat(hiveServices[0].getQueries()).isEqualTo(
                new String[]{"SELECT current_database()", "something more complex"});

        assertThat(hiveServices[1].getJdbcUrl()).isEqualTo("jdbc:hive2://knox-hive-server:10000/other-database");
        assertThat(hiveServices[1].getQueries()).isEqualTo(new String[]{"SELECT current_database()"});

        Config.WebHdfsService[] webHdfsServices = config.getWebHdfsServices();
        assertThat(webHdfsServices.length).isEqualTo(2);
        assertThat(webHdfsServices[0].getKnoxUrl()).isEqualTo("https://my-knox-server/gateway/default");
        assertThat(webHdfsServices[0].getStatusPaths()).isEqualTo(new String[]{"/", "/datalake"});
        assertThat(webHdfsServices[1].getKnoxUrl()).isEqualTo("https://my-knox-server/gateway/another-cluster");
        assertThat(webHdfsServices[1].getStatusPaths()).isEqualTo(new String[]{"/"});

        Config.HBaseService[] hBaseServices = config.getHbaseServices();
        assertThat(hBaseServices.length).isEqualTo(1);
        assertThat(hBaseServices[0].getKnoxUrl()).isEqualTo("https://localhost:8443/gateway/default");
    }

    @Test
    public void testEscacpeJdbcUrl() {
        assertThat(Config.HiveService.escapeJdbcUrl("jdbc:hive2://sandbox-hdp.hortonworks.com:8443/;" +
                "ssl=true;sslTrustStore=sandbox-hdp.hortonworks.com.p12;" +
                "trustStorePassword=changeit;transportMode=http;httpPath=gateway/default/hive")
        ).isEqualTo("jdbc:hive2://sandbox-hdp.hortonworks.com:8443/;" +
                "ssl=true;sslTrustStore=sandbox-hdp.hortonworks.com.p12;" +
                "trustStorePassword=***;transportMode=http;httpPath=gateway/default/hive");
    }
}
