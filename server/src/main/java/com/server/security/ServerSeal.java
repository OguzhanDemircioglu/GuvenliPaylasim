package com.server.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Sunucuda ikinci kilit (docs/Security.md S2). Parolayla kilitli özel anahtar paketi ve authSecret'ın bcrypt özeti,
 * veritabanına yazılmadan önce veritabanı dışında tutulan bir anahtarla (SERVER_SEAL_KEY) AES-256-GCM ile bir kez daha
 * şifrelenir. Sadece veritabanı dökümünü ele geçiren biri bu değerlerle parola deneyemez.
 * <p>
 * Biçim: {@code "s1:" + Base64(nonce(12) | şifreli metin | etiket(16))}. AAD kolonun adıdır; bir kolondaki değer başka
 * bir kolona taşınamaz. Önekli olmayan değer eski (henüz kilitlenmemiş) kayıttır ve olduğu gibi okunur;
 * V003_SealSecrets bunları açılışta kilitler.
 * <p>
 * Anahtar kaybolursa bütün hesaplar açılamaz hale gelir. Anahtar veritabanı yedeğinin yanında tutulmaz
 * (Devam_Rehberi, yedekleme kuralları).
 */
@Component
public class ServerSeal {

    static final String PREFIX = "s1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public ServerSeal(@Value("${app.security.seal-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SERVER_SEAL_KEY must be Base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("SERVER_SEAL_KEY must be 32 bytes (256 bit), Base64 encoded");
        }
        this.key = new SecretKeySpec(raw, "AES");
        Arrays.fill(raw, (byte) 0);
    }

    public String seal(String plain, String context) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[NONCE_LENGTH + sealed.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_LENGTH);
            System.arraycopy(sealed, 0, out, NONCE_LENGTH, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Server seal failed", e);
        }
    }

    /** Önekli değeri açar; öneksiz (eski) değeri olduğu gibi döner. Anahtar ya da bağlam yanlışsa hata. */
    public String open(String stored, String context) {
        if (stored == null || !isSealed(stored)) {
            return stored;
        }
        byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        if (raw.length < NONCE_LENGTH + TAG_BITS / 8) {
            throw new IllegalStateException("Sealed value is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, NONCE_LENGTH));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(raw, NONCE_LENGTH, raw.length - NONCE_LENGTH), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Sealed value cannot be opened: wrong SERVER_SEAL_KEY or tampered value");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Server seal open failed", e);
        }
    }

    public static boolean isSealed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
