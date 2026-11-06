package com.desktop.crypto;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Paroladan iki bağımsız değer türetilir (docs/Crypto_Design.md §2, v2: §7.3):
 * <ul>
 *   <li>{@code authSecret}: sunucuya "parola" yerine gönderilir. Sunucu gerçek parolayı hiç görmez.</li>
 *   <li>{@code kek}: özel anahtarları şifreleyen anahtar. Bu bilgisayardan hiç çıkmaz.</li>
 * </ul>
 * Tuz telefon numarasıdır (E.164, {@code +905XXXXXXXXX}). Sunucu tarafındaki {@code AuthSecrets} ile birebir aynı
 * hesabı yapar; biri değişirse diğeri de değişmelidir.
 */
public final class PasswordKeys {

    static final int ITERATIONS = 600_000;
    private static final String SALT_PREFIX = "guvenli-paylasim/v2|";

    private PasswordKeys() {
    }

    public record Derived(String authSecret, byte[] kek) {
    }

    /** {@code phoneE164}: {@code +905XXXXXXXXX}. */
    public static Derived derive(String phoneE164, char[] password) {
        byte[] master = null;
        try {
            byte[] salt = (SALT_PREFIX + phoneE164).getBytes(StandardCharsets.UTF_8);
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
            master = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(master, "auth"));
            return new Derived(auth, hmac(master, "key-encryption"));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("PBKDF2 kullanılamıyor", e);
        } finally {
            if (master != null) {
                Arrays.fill(master, (byte) 0);
            }
        }
    }

    private static byte[] hmac(byte[] key, String label) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
    }
}
