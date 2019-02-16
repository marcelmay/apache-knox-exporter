package de.m3y.prometheus.exporter.knox;

import java.io.File;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigLoaderTest {
    @Test
    public void testLoader() {
        final File configFile = new File("src/test/resources/" + ConfigTest.CONFIG_TEST_YML);
        ConfigLoader loader = ConfigLoader.forFile(configFile);
        Config config = loader.getCurrentConfig();
        ConfigTest.validateConfig(config);

        // Should not be reloaded
        assertThat(config == loader.getOrLoadIfModified()).isTrue();

        // Trigger reload
        configFile.setLastModified(System.currentTimeMillis());
        // Current config should not be automatically reloaded
        assertThat(config == loader.getCurrentConfig()).isTrue();
        // Check reload
        Config relodedConfig = loader.getOrLoadIfModified();
        assertThat(config != relodedConfig).isTrue();
        ConfigTest.validateConfig(config);
    }
}
