package com.desktop;

import java.util.Locale;

/**
 * Giriş noktası. JavaFX classpath'ten yüklendiğinde ana sınıfın Application'dan türememesi gerekir.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // Kurum proxy'si HTTPS trafiğini kendi sertifikasıyla yeniden imzalıyor; bu kök sertifika Java'nın değil
        // Windows'un sertifika deposunda. Güncelleme kontrolü (GitHub) için Windows deposu kullanılır.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                && System.getProperty("javax.net.ssl.trustStoreType") == null) {
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
        }
        System.setProperty("java.net.useSystemProxies", "true");
        App.main(args);
    }
}
