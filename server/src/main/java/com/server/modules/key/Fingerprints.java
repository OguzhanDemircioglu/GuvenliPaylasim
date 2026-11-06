package com.server.modules.key;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Açık anahtarların parmak izi (docs/Crypto_Design.md §3): SHA-256(şifreleme açık anahtarı || imza açık anahtarı)'nın
 * ilk 16 baytı, büyük harf hex, 4'lü gruplar. İstemcideki {@code UserKeys.fingerprint} ile birebir aynıdır.
 * Sunucu bunu sadece kişi kaydındaki parmak izinin güncel anahtarlarla uyuşup uyuşmadığını kontrol etmek için
 * kullanır (Crypto_Design §7.8).
 */
public final class Fingerprints {

    private Fingerprints() {
    }

    public static String of(String encryptionPublicKey, String signingPublicKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(Base64.getDecoder().decode(encryptionPublicKey));
            sha.update(Base64.getDecoder().decode(signingPublicKey));
            String hex = HexFormat.of().withUpperCase().formatHex(sha.digest(), 0, 16);
            StringBuilder grouped = new StringBuilder();
            for (int i = 0; i < hex.length(); i += 4) {
                if (i > 0) {
                    grouped.append(' ');
                }
                grouped.append(hex, i, i + 4);
            }
            return grouped.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String of(UserKey key) {
        return of(key.getEncryptionPublicKey(), key.getSigningPublicKey());
    }
}
