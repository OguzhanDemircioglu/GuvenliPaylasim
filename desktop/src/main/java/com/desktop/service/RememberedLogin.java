package com.desktop.service;

import com.desktop.api.ApiClient;
import com.desktop.crypto.Dpapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * "Beni hatırla" kaydı (Crypto_Design §7.4, Security.md): refresh token ve özel anahtarlar bu bilgisayarda, Windows
 * veri korumasıyla (DPAPI) şifreli bir dosyada durur. Dosya sadece aynı Windows kullanıcısında açılır. Kayıt
 * girişten itibaren 30 gün geçerlidir; çıkış yapınca, süre dolunca ya da sunucu oturumu kabul etmeyince silinir.
 */
public final class RememberedLogin {

    public static final Duration VALIDITY = Duration.ofDays(30);
    private static final byte[] ENTROPY = "gps-remember/v1".getBytes(StandardCharsets.UTF_8);

    /**
     * {@code kek}: kayda özel rastgele anahtar (Base64); {@code bundle} özel anahtarların bu anahtarla kilitli paketi
     * (UserKeys.seal). Paket ayrıca DPAPI ile şifreli dosyanın içindedir.
     */
    public record Data(String server, String phone, String refreshToken, long untilEpochSecond, String kek,
                       String bundle, String encryptionPublicKey, String signingPublicKey) {

        Data withRefreshToken(String token) {
            return new Data(server, phone, token, untilEpochSecond, kek, bundle, encryptionPublicKey, signingPublicKey);
        }
    }

    private final Path file;
    private volatile Data current;

    public RememberedLogin(Path file) {
        this.file = file;
    }

    /** {@code %LOCALAPPDATA%\GuvenliPaylasim\remember.bin}: dolaşan (roaming) profile kopyalanmaz. */
    public static RememberedLogin defaultLocation() {
        String base = System.getenv("LOCALAPPDATA");
        Path dir = base != null ? Path.of(base) : Path.of(System.getProperty("user.home"), "AppData", "Local");
        return new RememberedLogin(dir.resolve("GuvenliPaylasim").resolve("remember.bin"));
    }

    public boolean exists() {
        return Files.exists(file);
    }

    /** Bu sunucu için süresi dolmamış kayıt; bozuk, başka sunucuya ait ya da süresi dolmuş kayıt silinir. */
    public synchronized Optional<Data> load(String server, Instant now) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            byte[] plain = Dpapi.unprotect(Files.readAllBytes(file), ENTROPY);
            Data data = ApiClient.JSON.readValue(plain, Data.class);
            if (!server.equals(data.server()) || now.getEpochSecond() >= data.untilEpochSecond()) {
                clear();
                return Optional.empty();
            }
            current = data;
            return Optional.of(data);
        } catch (IOException | RuntimeException e) {
            clear();
            return Optional.empty();
        }
    }

    public synchronized void save(Data data) {
        try {
            Files.createDirectories(file.getParent());
            byte[] plain = ApiClient.JSON.writeValueAsBytes(data);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, Dpapi.protect(plain, ENTROPY));
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            current = data;
        } catch (IOException e) {
            throw new IllegalStateException("Oturum bu bilgisayara kaydedilemedi", e);
        }
    }

    /** Sunucu her yenilemede refresh token'ı değiştirir; kayıt en son token'ı tutmalı, yoksa sonraki açılış reddedilir. */
    public synchronized void updateRefreshToken(String token) {
        if (current != null && token != null && !token.equals(current.refreshToken())) {
            save(current.withRefreshToken(token));
        }
    }

    public synchronized void clear() {
        current = null;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Silinemezse bir sonraki açılışta sunucu eski token'ı reddeder ve kayıt yine silinir.
        }
    }
}
