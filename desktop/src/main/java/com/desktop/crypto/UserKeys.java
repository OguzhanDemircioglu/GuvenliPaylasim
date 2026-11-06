package com.desktop.crypto;

import javax.crypto.AEADBadTagException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Kullanıcının iki anahtar çifti: belge anahtarı sarmalamak için X25519, imzalamak için Ed25519.
 * Anahtarlar kayıtta (ve parola sıfırlamada) bu bilgisayarda üretilir. Sunucuya açık anahtarlar ve parolayla şifrelenmiş özel anahtar
 * paketi gider (docs/Crypto_Design.md §3).
 */
public record UserKeys(KeyPair encryption, KeyPair signing) {

    private static final byte BUNDLE_VERSION = 1;
    private static final String BUNDLE_AAD = "gps-private-keys/v2|";

    public static UserKeys generate() {
        try {
            return new UserKeys(KeyPairGenerator.getInstance("X25519").generateKeyPair(),
                    KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Anahtar üretilemedi", e);
        }
    }

    public String encryptionPublicKey() {
        return encode(encryption.getPublic());
    }

    public String signingPublicKey() {
        return encode(signing.getPublic());
    }

    /** Özel anahtarları paroladan türetilen anahtarla (kek) şifreler. Telefon, paketin başka hesaba taşınmasını önler. */
    public String seal(byte[] kek, String phoneE164) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            byte[] enc = encryption.getPrivate().getEncoded();
            byte[] sign = signing.getPrivate().getEncoded();
            out.writeShort(enc.length);
            out.write(enc);
            out.writeShort(sign.length);
            out.write(sign);
            byte[] sealed = Aead.encrypt(kek, bytes.toByteArray(), aad(phoneE164));

            byte[] bundle = new byte[1 + sealed.length];
            bundle[0] = BUNDLE_VERSION;
            System.arraycopy(sealed, 0, bundle, 1, sealed.length);
            return Base64.getEncoder().encodeToString(bundle);
        } catch (IOException e) {
            throw new CryptoException("Anahtar paketi oluşturulamadı", e);
        }
    }

    /**
     * Sunucudaki paketi açar. Açılan özel anahtarların kayıtlı açık anahtarlarla eşleştiği de kontrol edilir;
     * eşleşmezse paket başka bir anahtar çiftine aittir ve kullanılmaz.
     */
    public static UserKeys open(String bundle, byte[] kek, String phoneE164, String encryptionPublicKey,
                                String signingPublicKey) {
        byte[] raw = Base64.getDecoder().decode(bundle);
        if (raw.length < 2 || raw[0] != BUNDLE_VERSION) {
            throw new CryptoException("Anahtar paketinin biçimi tanınmıyor");
        }
        byte[] plain;
        try {
            plain = Aead.decrypt(kek, Arrays.copyOfRange(raw, 1, raw.length), aad(phoneE164));
        } catch (AEADBadTagException e) {
            throw new CryptoException("Özel anahtarlar bu parolayla açılamadı");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain))) {
            byte[] enc = in.readNBytes(in.readUnsignedShort());
            byte[] sign = in.readNBytes(in.readUnsignedShort());
            UserKeys keys = new UserKeys(
                    new KeyPair(publicKey("X25519", encryptionPublicKey), privateKey("X25519", enc)),
                    new KeyPair(publicKey("Ed25519", signingPublicKey), privateKey("Ed25519", sign)));
            keys.requireMatchingPairs();
            return keys;
        } catch (IOException e) {
            throw new CryptoException("Anahtar paketi bozuk", e);
        }
    }

    /**
     * Açık anahtarların parmak izi. Kullanıcılar bunu telefonla ya da yüz yüze karşılaştırarak sunucunun araya
     * sahte bir anahtar koymadığını doğrulayabilir.
     */
    public static String fingerprint(String encryptionPublicKey, String signingPublicKey) {
        String hex = HexFormat.of().withUpperCase().formatHex(fingerprintBytes(encryptionPublicKey, signingPublicKey));
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) {
                grouped.append(' ');
            }
            grouped.append(hex, i, i + 4);
        }
        return grouped.toString();
    }

    /** Parmak izinin ham hali (16 bayt): davet kutusuna ve kabul mührüne bu girer (Crypto_Design §7.6). */
    static byte[] fingerprintBytes(String encryptionPublicKey, String signingPublicKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(Base64.getDecoder().decode(encryptionPublicKey));
            sha.update(Base64.getDecoder().decode(signingPublicKey));
            return Arrays.copyOf(sha.digest(), 16);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Parmak izi hesaplanamadı", e);
        }
    }

    public String fingerprint() {
        return fingerprint(encryptionPublicKey(), signingPublicKey());
    }

    static PublicKey publicKey(String algorithm, String base64) {
        try {
            return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new CryptoException("Açık anahtar geçersiz", e);
        }
    }

    private static PrivateKey privateKey(String algorithm, byte[] pkcs8) {
        try {
            return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Özel anahtar geçersiz", e);
        }
    }

    /** İmzala-doğrula ve sarmala-aç ile özel anahtarların açık anahtarlara ait olduğunu kanıtlar. */
    private void requireMatchingPairs() {
        byte[] probe = Aead.randomKey();
        byte[] signature = Signatures.sign(signing.getPrivate(), probe);
        if (!Signatures.verify(signing.getPublic(), probe, signature)) {
            throw new CryptoException("İmza anahtarı kayıtlı açık anahtarla eşleşmiyor");
        }
        try {
            byte[] wrapped = KeyWrap.wrap(probe, encryption.getPublic(), probe);
            if (Arrays.equals(probe, KeyWrap.unwrap(wrapped, encryption, probe))) {
                return;
            }
        } catch (CryptoException ignored) {
            // Aşağıdaki açık mesajla bildirilir.
        }
        throw new CryptoException("Şifreleme anahtarı kayıtlı açık anahtarla eşleşmiyor");
    }

    private static byte[] aad(String phoneE164) {
        return Aead.utf8(BUNDLE_AAD + phoneE164);
    }

    private static String encode(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
