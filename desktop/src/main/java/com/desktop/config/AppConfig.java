package com.desktop.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

/**
 * Ayarlar: önce {@code -Dgps.<anahtar>}, sonra {@code GPS_<ANAHTAR>} ortam değişkeni, en son app.properties.
 */
public final class AppConfig {

    private static final AppConfig INSTANCE = load();

    private final Properties defaults;
    private final String version;

    private AppConfig(Properties defaults, String version) {
        this.defaults = defaults;
        this.version = version;
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    public String serverUrl() {
        return stripTrailingSlash(value("server.url"));
    }

    /** Sunucu sertifikası için sabitlenmiş açık anahtarlar (PinnedTrust). */
    public java.util.Set<String> serverPins() {
        return com.desktop.api.PinnedTrust.parse(value("server.pins"));
    }

    public String updateRepository() {
        return value("update.repository").trim();
    }

    public String updateApiUrl() {
        return stripTrailingSlash(value("update.api-url"));
    }

    public Duration updateGracePeriod() {
        return Duration.ofDays(Long.parseLong(value("update.grace-days")));
    }

    public Duration inboxPollInterval() {
        return Duration.ofSeconds(Long.parseLong(value("inbox.poll-seconds")));
    }

    /** Derlemede pom.xml'deki sürüm yazılır (app-version.properties). */
    public String version() {
        return version;
    }

    private String value(String key) {
        String system = System.getProperty("gps." + key);
        if (system != null) {
            return system;
        }
        String env = System.getenv("GPS_" + key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
        if (env != null) {
            return env;
        }
        return defaults.getProperty(key, "");
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static AppConfig load() {
        Properties defaults = read("app.properties");
        String version = read("app-version.properties").getProperty("version", "0.0.0");
        return new AppConfig(defaults, version);
    }

    private static Properties read(String name) {
        Properties properties = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/com/desktop/" + name)) {
            if (in != null) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
