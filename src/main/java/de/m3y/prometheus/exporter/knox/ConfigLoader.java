package de.m3y.prometheus.exporter.knox;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.yaml.snakeyaml.Yaml;

/**
 * Manages config loading and modification check.
 */
public interface ConfigLoader {
    /**
     * Gets current config, without reloading if modified.
     *
     * @return the current config.
     */
    Config getCurrentConfig();

    /**
     * Gets current or updated (if modified) config.
     *
     * @return the latest config.
     */
    Config getOrLoadIfModified();

    /**
     * Checks if current config has been modified.
     *
     * @return true, if modified.
     */
    boolean hasModifications();

    /**
     * Creates a file based config loader.
     *
     * @param configFile the config file to watch.
     * @return a new config loader.
     */
    static ConfigLoader forFile(File configFile) {
        return new FileConfigLoader(configFile);
    }

    class FileConfigLoader implements ConfigLoader {
        private final File filename;
        private long lastModifiedTimestamp;
        private Config config;

        FileConfigLoader(File filename) {
            this.filename = filename;
        }

        public synchronized Config getCurrentConfig() {
            if(null==config) {
                return getOrLoadIfModified(); // Load if not initialized yet
            }
            return config;
        }

        public synchronized Config getOrLoadIfModified() {
            // Check if previous config exists and if there are no intermediate modifications since last load
            if (null != config && !hasModifications()) {
                return config;
            }

            // Load
            try (FileReader reader = new FileReader(filename)) {
                lastModifiedTimestamp = filename.lastModified();
                config = new Yaml().loadAs(reader, Config.class);
                return config;
            } catch (IOException e) {
                throw new IllegalStateException("Can not load config from file " + filename, e);
            }
        }

        public boolean hasModifications() {
            return filename.lastModified() > lastModifiedTimestamp;
        }
    }
}
