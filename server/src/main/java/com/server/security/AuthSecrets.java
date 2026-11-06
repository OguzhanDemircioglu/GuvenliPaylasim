package com.server.security;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Sunucu kullanıcının gerçek parolasını hiç görmez (docs/Crypto_Design.md §2).
 * <p>
 * Masaüstü istemci paroladan bir ana anahtar türetir ve bundan iki bağımsız değer üretir:
 * <ul>
 *   <li>{@code authSecret}: sunucuya "parola" olarak gönderilir; sunucu bunu bcrypt ile saklar.</li>
 *   <li>{@code kek}: özel anahtarları şifreler; bilgisayardan hiç çıkmaz.</li>
 * </ul>
 * Sunucu authSecret'tan kek'i hesaplayamaz (iki değer ana anahtardan farklı etiketlerle HMAC ile ayrılır).
 * <p>
 * Bu sınıf istemcideki {@code PasswordKeys} ile birebir aynı hesabı yapar (v2: tuz telefon, Crypto_Design §7.3).
 * Sunucu tarafında yalnızca demo kullanıcıları oluşturulurken kullanılır; istemciden gelen değerler olduğu gibi
 * bcrypt'e verilir.
 */
public final class AuthSecrets {

    public static final int ITERATIONS = 600_000;
    private static final String SALT_PREFIX = "guvenli-paylasim/v2|";
    private static final String AUTH_LABEL = "auth";

    private AuthSecrets() {
    }

    /** {@code phoneE164}: {@code +905XXXXXXXXX}. */
    public static String derive(String phoneE164, String password) {
        try {
            byte[] salt = (SALT_PREFIX + phoneE164).getBytes(StandardCharsets.UTF_8);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            byte[] master = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            byte[] auth = mac.doFinal(AUTH_LABEL.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2/HMAC not available", e);
        }
    }
}
