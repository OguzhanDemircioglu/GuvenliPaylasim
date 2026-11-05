package com.desktop.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM. Çıktı biçimi: {@code nonce(12) | şifreli metin | etiket(16)}.
 * GCM hem gizlilik hem bütünlük sağlar: tek bir bit değişse bile çözme başarısız olur.
 */
final class Aead {

    static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private Aead() {
    }

    static byte[] randomKey() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return key;
    }

    static byte[] encrypt(byte[] key, byte[] plain, byte[] aad) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] sealed = cipher.doFinal(plain);
            byte[] out = new byte[NONCE_LENGTH + sealed.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_LENGTH);
            System.arraycopy(sealed, 0, out, NONCE_LENGTH, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Şifreleme yapılamadı", e);
        }
    }

    /** @throws AEADBadTagException anahtar yanlışsa ya da veri değiştirilmişse */
    static byte[] decrypt(byte[] key, byte[] sealed, byte[] aad) throws AEADBadTagException {
        if (sealed.length < NONCE_LENGTH + TAG_BITS / 8) {
            throw new AEADBadTagException("too short");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(sealed, 0, NONCE_LENGTH)));
            cipher.updateAAD(aad);
            return cipher.doFinal(sealed, NONCE_LENGTH, sealed.length - NONCE_LENGTH);
        } catch (AEADBadTagException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Şifre çözülemedi", e);
        }
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
