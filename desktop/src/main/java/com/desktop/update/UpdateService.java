package com.desktop.update;

import com.desktop.api.ApiClient;
import com.desktop.config.AppConfig;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.prefs.Preferences;

/**
 * GitHub Releases üzerinden güncelleme (docs/Update_Design.md).
 * <ul>
 *   <li>En son sürümün {@code release.json} dosyası okunur: sürüm, kurulum dosyası, SHA-256 ve imza.</li>
 *   <li>Sürüm yayımlandıktan sonra {@code update.grace-days} gün kullanıcıya süre tanınır; süre bitince ya da sürüm
 *       zorunlu işaretliyse uygulama güncellenmeden açılmaz.</li>
 *   <li>GitHub'a ulaşılamazsa daha önce görülen sürüm bilgisi kullanılır: süresi geçmiş bir güncelleme varsa
 *       uygulama yine açılmaz.</li>
 * </ul>
 * Kurum ağı proxy üzerinden çıktığı için proxy {@code HTTPS_PROXY} ortam değişkeninden ya da Windows ayarından
 * alınır.
 */
public final class UpdateService {

    private static final String MANIFEST_ASSET = "release.json";
    private static final Preferences PREFS = Preferences.userNodeForPackage(UpdateService.class);

    /** Kullanıcıya gösterilecek güncelleme bilgisi. */
    public record Release(Version version, String notes, Instant deadline, boolean mandatory, String installerName,
                          String installerUrl, String sha256, String signature) {

        public boolean blocking(Instant now) {
            return mandatory || !now.isBefore(deadline);
        }
    }

    public sealed interface Check {
        record UpToDate() implements Check {
        }

        record Available(Release release) implements Check {
        }

        /** GitHub'a ulaşılamadı ama daha önce süresi dolmuş bir güncelleme görülmüştü. */
        record OfflineBlocked(Version required) implements Check {
        }

        record Unavailable(String reason) implements Check {
        }
    }

    private final AppConfig config;
    private final Version current;
    private final Clock clock;
    private final HttpClient http;

    public UpdateService(AppConfig config, Clock clock) {
        this.config = config;
        this.current = Version.parse(config.version());
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .proxy(proxy())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public Version current() {
        return current;
    }

    public boolean enabled() {
        return !config.updateRepository().isBlank();
    }

    public Check check() {
        if (!enabled()) {
            return new Check.UpToDate();
        }
        try {
            Release release = latest();
            if (release == null || !release.version().isNewerThan(current)) {
                PREFS.remove("required.version");
                return new Check.UpToDate();
            }
            PREFS.put("required.version", release.version().toString());
            PREFS.putLong("required.deadline", release.mandatory() ? 0 : release.deadline().toEpochMilli());
            return new Check.Available(release);
        } catch (IOException | RuntimeException e) {
            return offlineDecision(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return offlineDecision(e);
        }
    }

    /** Kurulum dosyasını indirir, özetini ve imzasını doğrular. Doğrulanamazsa dosya silinir. */
    public Path download(Release release, DoubleConsumer progress) throws IOException, InterruptedException {
        PublicKey key;
        try {
            key = ReleaseSignature.publicKey(embeddedPublicKey());
        } catch (GeneralSecurityException e) {
            throw new IOException("Uygulamadaki güncelleme anahtarı okunamadı", e);
        }
        if (!ReleaseSignature.verify(key, release.version().toString(), release.sha256(), release.mandatory(),
                release.signature())) {
            throw new IOException("Sürüm imzası geçersiz; güncelleme kurulmadı.");
        }

        HttpResponse<InputStream> response = http.send(request(release.installerUrl())
                .header("Accept", "application/octet-stream").build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Kurulum dosyası indirilemedi (HTTP " + response.statusCode() + ")");
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        Path target = Files.createTempDirectory("gps-update").resolve(release.installerName());
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        try (InputStream in = new DigestInputStream(response.body(), sha);
             var out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long read = 0;
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
                read += n;
                if (total > 0) {
                    progress.accept((double) read / total);
                }
            }
        }
        String actual = HexFormat.of().formatHex(sha.digest());
        if (!actual.equalsIgnoreCase(release.sha256())) {
            Files.deleteIfExists(target);
            throw new IOException("İndirilen dosyanın özeti imzalı özetle uyuşmuyor; güncelleme kurulmadı.");
        }
        return target;
    }

    /** Doğrulanmış kurulum dosyasını başlatır. MSI, Windows Installer ile açılır. */
    public void launchInstaller(Path installer) throws IOException {
        String name = installer.getFileName().toString().toLowerCase();
        ProcessBuilder process = name.endsWith(".msi")
                ? new ProcessBuilder("msiexec", "/i", installer.toString())
                : new ProcessBuilder(installer.toString());
        process.start();
    }

    private Release latest() throws IOException, InterruptedException {
        String url = config.updateApiUrl() + "/repos/" + config.updateRepository() + "/releases/latest";
        HttpResponse<String> response = http.send(request(url).header("Accept", "application/vnd.github+json").build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub HTTP " + response.statusCode());
        }
        JsonNode release = ApiClient.JSON.readTree(response.body());
        Instant published = Instant.parse(release.path("published_at").asString());

        String manifestUrl = null;
        String installerUrl = null;
        for (JsonNode asset : release.path("assets")) {
            if (MANIFEST_ASSET.equals(asset.path("name").asString())) {
                manifestUrl = asset.path("browser_download_url").asString();
            }
        }
        if (manifestUrl == null) {
            throw new IOException("Sürümde " + MANIFEST_ASSET + " yok");
        }
        HttpResponse<String> manifestResponse = http.send(request(manifestUrl).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode manifest = ApiClient.JSON.readTree(manifestResponse.body());
        String installerName = manifest.path("installer").asString();
        for (JsonNode asset : release.path("assets")) {
            if (installerName.equals(asset.path("name").asString())) {
                installerUrl = asset.path("browser_download_url").asString();
            }
        }
        if (installerUrl == null) {
            throw new IOException("Sürümde kurulum dosyası yok: " + installerName);
        }
        return new Release(Version.parse(manifest.path("version").asString()),
                release.path("body").asString(""),
                published.plus(config.updateGracePeriod()),
                manifest.path("mandatory").asBoolean(false),
                installerName, installerUrl,
                manifest.path("sha256").asString(), manifest.path("signature").asString());
    }

    private Check offlineDecision(Exception cause) {
        String required = PREFS.get("required.version", null);
        if (required != null && Version.parse(required).isNewerThan(current)) {
            long deadline = PREFS.getLong("required.deadline", Long.MAX_VALUE);
            if (!clock.instant().isBefore(Instant.ofEpochMilli(deadline))) {
                return new Check.OfflineBlocked(Version.parse(required));
            }
        }
        return new Check.Unavailable(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "GuvenliPaylasim-Updater");
    }

    private static String embeddedPublicKey() throws IOException {
        try (InputStream in = UpdateService.class.getResourceAsStream("/com/desktop/update-public-key.txt")) {
            if (in == null) {
                throw new IOException("update-public-key.txt bulunamadı");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Önce HTTPS_PROXY (kurumda tanımlı), yoksa Windows'un proxy ayarı. */
    static ProxySelector proxy() {
        String env = Optional.ofNullable(System.getenv("HTTPS_PROXY")).orElse(System.getenv("https_proxy"));
        if (env != null && !env.isBlank()) {
            URI uri = URI.create(env.contains("://") ? env : "http://" + env);
            return ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 8080));
        }
        return ProxySelector.getDefault();
    }
}
