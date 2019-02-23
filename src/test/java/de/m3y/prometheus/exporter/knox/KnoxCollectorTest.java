package de.m3y.prometheus.exporter.knox;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import static de.m3y.prometheus.assertj.MetricFamilySamplesAssert.assertThat;
import static de.m3y.prometheus.assertj.MetricFamilySamplesUtils.getMetricFamilySamples;

public class KnoxCollectorTest {
    private static final File CONFIG_FILE = new File("src/test/resources/config-it.yml");

    @Test
    public void testConfigReloads() {
        final AtomicBoolean modified = new AtomicBoolean();
        modified.set(false);

        ConfigLoader configLoader = new ConfigLoader.FileConfigLoader(CONFIG_FILE) {
            @Override
            public synchronized boolean hasModifications() {
                return modified.get();
            }
        };
        KnoxCollector knoxCollector = new KnoxCollector(configLoader);
        knoxCollector.collect();
        knoxCollector.collect();

        assertThat(getMetricFamilySamples(knoxCollector.collect(), "knox_exporter_config_reloads_total"))
                .hasTypeOfCounter()
                .hasSampleValue(0.0);

        // Force reloads
        modified.set(true);

        assertThat(getMetricFamilySamples(knoxCollector.collect(),"knox_exporter_config_reloads_total"))
                .hasTypeOfCounter()
                .hasSampleValue(1.0); // Another inc as config flagged as modified


        assertThat(getMetricFamilySamples(knoxCollector.collect(),"knox_exporter_config_reloads_total"))
                .hasTypeOfCounter()
                .hasSampleValue(2.0); // Another inc as config flagged as modified

        modified.set(false);

        assertThat(getMetricFamilySamples(knoxCollector.collect(),"knox_exporter_config_reloads_total"))
                .hasTypeOfCounter()
                .hasSampleValue(2.0); // No increment
    }
}
