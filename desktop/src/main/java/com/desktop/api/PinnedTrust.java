package com.desktop.api;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sertifika sabitleme (docs/Security.md S1). Sunucu sertifikası önce Java'nın güven deposuyla normal şekilde
 * doğrulanır; ardından doğrulanmış zincirde (kök dahil) sabitlenmiş anahtarlardan biri yoksa bağlantı reddedilir.
 * Böylece güven deposundaki başka bir sertifika otoritesinden alınmış sahte bir sertifikayla araya girilemez.
 * <p>
 * Sabit: açık anahtarın (SubjectPublicKeyInfo, DER) SHA-256'sı, Base64. Demo sunucusu Railway'de olduğu ve
 * sertifikayı Railway yönettiği için sabitleme sunucunun kendi anahtarına değil Let's Encrypt köklerine yapılır
 * (Security.md S1, demo sınırı). Kök anahtarlar yıllarca değişmez; ara sertifikalar yıllık değiştiği için onlara
 * sabitleme yapılmaz. Bağlantılar sadece TLS 1.3 ile kurulur.
 */
public final class PinnedTrust {

    /** Sadece TLS 1.3 (Security.md S1). */
    public static final String[] PROTOCOLS = {"TLSv1.3"};

    private PinnedTrust() {
    }

    /** {@code pins} boşsa sabitleme yapılmaz, sadece normal doğrulama ve TLS 1.3 uygulanır. */
    public static SSLContext context(Set<String> pins) {
        try {
            KeyStore anchors = systemTrustStore();
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(anchors);
            X509TrustManager standard = Arrays.stream(factory.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast)
                    .findFirst().orElseThrow();
            TrustManager manager = pins.isEmpty() ? standard : new Pinning(standard, anchors, pins);
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(null, new TrustManager[]{manager}, null);
            return context;
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("TLS could not be configured", e);
        }
    }

    public static SSLParameters parameters() {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(PROTOCOLS);
        return parameters;
    }

    /** SubjectPublicKeyInfo'nun SHA-256'sı, Base64 (HPKP biçimi). */
    public static String pin(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Doğrulanmış zincirdeki sertifikalardan biri sabitlenmiş anahtarı taşıyor mu? */
    static boolean matches(Collection<X509Certificate> path, Set<String> pins) {
        return path.stream().map(PinnedTrust::pin).anyMatch(pins::contains);
    }

    /**
     * Java güven deposu + Windows'ta Windows güven deposu (Windows-ROOT). Kurum ağları HTTPS'i kendi kökleriyle
     * yeniden imzalayabilir (ör. Fortinet); o kök Windows'a kurulu olur. Bu kökler sadece normal doğrulamayı geçer,
     * sabitlemeyi geçmez: kabul edilmesi için pin'inin {@code GPS_SERVER_PINS} ile o makinede eklenmesi gerekir.
     */
    static KeyStore systemTrustStore() throws GeneralSecurityException, java.io.IOException {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Files.newInputStream(Path.of(System.getProperty("java.home"), "lib", "security", "cacerts"))) {
            store.load(in, null);
        }
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            KeyStore windows = KeyStore.getInstance("Windows-ROOT");
            windows.load(null, null);
            int index = 0;
            for (String alias : java.util.Collections.list(windows.aliases())) {
                java.security.cert.Certificate certificate = windows.getCertificate(alias);
                if (certificate != null && store.getCertificateAlias(certificate) == null) {
                    store.setCertificateEntry("windows-root-" + index++, certificate);
                }
            }
        }
        return store;
    }

    private static final class Pinning implements X509TrustManager {

        private final X509TrustManager standard;
        private final KeyStore anchors;
        private final Set<String> pins;

        Pinning(X509TrustManager standard, KeyStore anchors, Set<String> pins) {
            this.standard = standard;
            this.anchors = anchors;
            this.pins = Set.copyOf(pins);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            standard.checkServerTrusted(chain, authType);
            List<X509Certificate> path = validatedPath(chain);
            if (!matches(path, pins)) {
                throw new CertificateException("Sunucu sertifikası beklenen kök sertifikaya zincirlenmiyor (sabitleme)");
            }
        }

        /** Java'nın kurduğu doğrulanmış yol: sunucu sertifikasından güven deposundaki köke kadar, kök dahil. */
        private List<X509Certificate> validatedPath(X509Certificate[] chain) throws CertificateException {
            try {
                X509CertSelector target = new X509CertSelector();
                target.setCertificate(chain[0]);
                PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, target);
                parameters.setRevocationEnabled(false);
                parameters.addCertStore(CertStore.getInstance("Collection",
                        new CollectionCertStoreParameters(Arrays.asList(chain))));
                PKIXCertPathBuilderResult result =
                        (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parameters);
                List<X509Certificate> path = new ArrayList<>();
                result.getCertPath().getCertificates().forEach(c -> path.add((X509Certificate) c));
                if (result.getTrustAnchor().getTrustedCert() != null) {
                    path.add(result.getTrustAnchor().getTrustedCert());
                }
                return path;
            } catch (GeneralSecurityException e) {
                throw new CertificateException("Sunucu sertifikasının zinciri kurulamadı", e);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client certificates are not used");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return standard.getAcceptedIssuers();
        }
    }

    /** {@code "a,b , c"} → küme; boş girdi boş küme. */
    public static Set<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
