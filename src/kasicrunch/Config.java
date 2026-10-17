/*
 * File:    Config.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Loads runtime settings (database connection, server port) from
 *          config.properties, letting environment variables override them so
 *          the same build can run locally or on a cloud host.
 */
package kasicrunch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {

    private final Properties properties = new Properties();

    private Config() {
    }

    /** Reads config.properties from the working directory, if present. */
    public static Config load() {
        Config config = new Config();
        Path file = Path.of("config.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                config.properties.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read config.properties: " + e.getMessage(), e);
            }
        }
        return config;
    }

    /**
     * Looks up a setting. An environment variable such as KASICRUNCH_DB_HOST
     * wins over the db.host entry in the file, which wins over the default.
     */
    public String get(String key, String defaultValue) {
        String envName = "KASICRUNCH_" + key.toUpperCase().replace('.', '_');
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String dbHost() { return get("db.host", "localhost"); }
    public int dbPort() { return getInt("db.port", 3306); }
    public String dbName() { return get("db.name", "kasicrunch"); }
    public String dbUser() { return get("db.user", "root"); }
    public String dbPassword() { return get("db.password", ""); }
    public int serverPort() { return getInt("server.port", 8080); }
    public String adminEmail() { return get("admin.email", "admin@kasicrunch.local"); }
    public String adminPassword() { return get("admin.password", "Admin@2026"); }
}
