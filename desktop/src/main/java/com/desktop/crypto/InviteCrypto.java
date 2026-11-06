package com.desktop.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.KDF;
import javax.crypto.Mac;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Davet kodu (docs/Crypto_Design.md §7.6). Kod hem izin hem anahtardır; sunucuya hiç gitmez.
 * <pre>
 *   kod         = 16 karakter Crockford Base32 (80 bit), ekranda XXXX-XXXX-XXXX-XXXX
 *   kodKey      = HKDF-SHA256(kod, tuz yok, "gps-invite-key/v1")
 *   doğrulayıcı = HKDF-SHA256(kod, tuz yok, "gps-invite-verify/v1")   → sunucuya SHA-256'sı gider
 *   mühürKey    = HKDF-SHA256(kod, tuz yok, "gps-invite-mac/v1")      → gönderenin kendi anahtarına sarmalanır
 *   sarmalı     = 0x01 | AES-256-GCM(kodKey, K || gönderen parmak izi (16 bayt), AAD = SHA-256(payload))
 *   mühür       = HMAC-SHA256(mühürKey, "gps-invite-accept/v1" || gönderen parmak izi || alıcı parmak izi)
 *   kişi imzası = Ed25519(sahip, "gps-contact/v1|" + sahip telefonu + "|" + kişi telefonu + "|" + parmak izi)
 * </pre>
 * HKDF girdisi kodun tiresiz, büyük harfli 16 ASCII karakteridir.
 * <p>
 * İki yönlü doğrulama: alıcı göndereni kutunun içindeki parmak iziyle, gönderen alıcıyı kodla mühürlenmiş parmak
 * iziyle doğrular; sunucu iki yönde de araya sahte anahtar koyamaz (Security.md S3).
 */
public final class InviteCrypto {

    /** Crockford Base32: I, L, O, U yok; karıştırılan harfler girişte düzeltilir. */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int CODE_LENGTH = 16;
    private static final byte WRAP_VERSION = 1;
    private static final int KEY_LENGTH = 32;
    private static final int FINGERPRINT_LENGTH = 16;

    private InviteCrypto() {
    }

    // ---------- Kod ----------

    /** 80 bit rastgele kod, tiresiz. */
    public static String generateCode() {
        byte[] random = new byte[10];
        Aead.RANDOM.nextBytes(random);
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        long high = 0;
        for (int i = 0; i < 5; i++) {
            high = (high << 8) | (random[i] & 0xFF);
        }
        long low = 0;
        for (int i = 5; i < 10; i++) {
            low = (low << 8) | (random[i] & 0xFF);
        }
        for (long part : new long[]{high, low}) {
            for (int shift = 35; shift >= 0; shift -= 5) {
                code.append(ALPHABET.charAt((int) ((part >> shift) & 31)));
            }
        }
        return code.toString();
    }

    /** {@code K7M2Q9XA4TPBW8RC} → {@code K7M2-Q9XA-4TPB-W8RC}. */
    public static String format(String code) {
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8, 12) + "-" + code.substring(12);
    }

    /** Kullanıcının yazdığını kanonik koda çevirir; geçersizse null. Boşluk ve tireler atılır, O→0, I/L→1. */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String code = input.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "")
                .replace('O', '0').replace('I', '1').replace('L', '1');
        if (code.length() != CODE_LENGTH) {
            return null;
        }
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return code;
    }

    // ---------- Gönderen: davet oluşturma ----------

    public record Created(String code, byte[] payload, String signature, String wrapped, String verifierHash,
                          String selfWrapped) {
    }

    public static Created create(DocumentContent content, UserKeys sender) {
        String code = generateCode();
        byte[] documentKey = Aead.randomKey();
        byte[] codeKey = derive(code, "gps-invite-key/v1");
        byte[] macKey = derive(code, "gps-invite-mac/v1");
        try {
            DocumentCrypto.Signed signed = DocumentCrypto.sealPayload(content, sender, documentKey);
            byte[] plain = concat(documentKey, UserKeys.fingerprintBytes(sender.encryptionPublicKey(),
                    sender.signingPublicKey()));
            byte[] sealed = Aead.encrypt(codeKey, plain, DocumentCrypto.sha256(signed.payload()));
            Arrays.fill(plain, (byte) 0);
            byte[] wrapped = new byte[1 + sealed.length];
            wrapped[0] = WRAP_VERSION;
            System.arraycopy(sealed, 0, wrapped, 1, sealed.length);

            String verifierHash = HexFormat.of().formatHex(DocumentCrypto.sha256(derive(code, "gps-invite-verify/v1")));
            String selfWrapped = Base64.getEncoder().encodeToString(
                    KeyWrap.wrap(macKey, sender.encryption().getPublic(), selfBinding(verifierHash)));
            return new Created(code, signed.payload(), signed.signature(), Base64.getEncoder().encodeToString(wrapped),
                    verifierHash, selfWrapped);
        } finally {
            Arrays.fill(documentKey, (byte) 0);
            Arrays.fill(codeKey, (byte) 0);
            Arrays.fill(macKey, (byte) 0);
        }
    }

    // ---------- Alıcı: kodu girme ----------

    /** Sunucuya gönderilen doğrulayıcı (Base64). Sunucu bunun SHA-256'sını arar. */
    public static String verifier(String code) {
        return Base64.getEncoder().encodeToString(derive(code, "gps-invite-verify/v1"));
    }

    public record Accepted(String wrappedKey, String seal, String senderFingerprint, String contactSignature) {
    }

    /**
     * Kutuyu kodla açar ve doğrular: gönderenin sunucudan gelen anahtarlarının parmak izi kutunun içindekiyle aynı
     * olmalı ve imza geçerli olmalı. Ardından belge anahtarı alıcının kendi anahtarına yeniden sarmalanır, kabul
     * mührü ve alıcının kişi kaydı imzası üretilir.
     */
    public static Accepted accept(String code, byte[] payload, String signature, String wrapped,
                                  String senderEncryptionKey, String senderSigningKey, String senderPhone,
                                  UserKeys own, String ownPhone) {
        byte[] raw = Base64.getDecoder().decode(wrapped);
        if (raw.length < 2 || raw[0] != WRAP_VERSION) {
            throw new CryptoException("Davet kutusunun biçimi tanınmıyor");
        }
        byte[] codeKey = derive(code, "gps-invite-key/v1");
        byte[] macKey = derive(code, "gps-invite-mac/v1");
        byte[] plain = null;
        try {
            try {
                plain = Aead.decrypt(codeKey, Arrays.copyOfRange(raw, 1, raw.length), DocumentCrypto.sha256(payload));
            } catch (AEADBadTagException e) {
                throw new CryptoException("Kod bu kutuyu açmıyor ya da kutu değiştirilmiş");
            }
            if (plain.length != KEY_LENGTH + FINGERPRINT_LENGTH) {
                throw new CryptoException("Davet kutusunun biçimi tanınmıyor");
            }
            byte[] senderFingerprint = UserKeys.fingerprintBytes(senderEncryptionKey, senderSigningKey);
            if (!MessageDigest.isEqual(senderFingerprint, Arrays.copyOfRange(plain, KEY_LENGTH, plain.length))) {
                throw new CryptoException("Gönderenin anahtarı kutudakiyle uyuşmuyor: sunucu araya sahte bir anahtar "
                        + "koymuş olabilir. Kutu açılmadı.");
            }
            if (!DocumentCrypto.signatureValid(payload, signature, senderSigningKey)) {
                throw new CryptoException("İmza doğrulanamadı: kutu değiştirilmiş ya da gönderen bu kişi değil");
            }
            byte[] documentKey = Arrays.copyOf(plain, KEY_LENGTH);
            try {
                String wrappedKey = DocumentCrypto.wrapFor(documentKey, own.encryptionPublicKey(), payload);
                byte[] ownFingerprint = UserKeys.fingerprintBytes(own.encryptionPublicKey(), own.signingPublicKey());
                String seal = Base64.getEncoder().encodeToString(seal(macKey, senderFingerprint, ownFingerprint));
                String senderFp = UserKeys.fingerprint(senderEncryptionKey, senderSigningKey);
                return new Accepted(wrappedKey, seal, senderFp, signContact(own, ownPhone, senderPhone, senderFp));
            } finally {
                Arrays.fill(documentKey, (byte) 0);
            }
        } finally {
            Arrays.fill(codeKey, (byte) 0);
            Arrays.fill(macKey, (byte) 0);
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }

    // ---------- Gönderen: kabulü doğrulama ----------

    /**
     * Alıcının mührünü doğrular: mühürKey gönderenin kendi özel anahtarıyla açılır, alıcının sunucudan gelen güncel
     * anahtarlarının parmak iziyle mühür yeniden hesaplanır. Tutarsa gönderenin kişi kaydı imzası döner; tutmazsa
     * sunucu alıcı yerine sahte anahtar vermiş demektir.
     */
    public static String verifyAcceptance(String selfWrapped, String verifierHash, String seal,
                                          String recipientEncryptionKey, String recipientSigningKey,
                                          String recipientPhone, UserKeys own, String ownPhone) {
        byte[] macKey = KeyWrap.unwrap(Base64.getDecoder().decode(selfWrapped), own.encryption(),
                selfBinding(verifierHash));
        try {
            byte[] expected = seal(macKey,
                    UserKeys.fingerprintBytes(own.encryptionPublicKey(), own.signingPublicKey()),
                    UserKeys.fingerprintBytes(recipientEncryptionKey, recipientSigningKey));
            if (!MessageDigest.isEqual(expected, Base64.getDecoder().decode(seal))) {
                throw new CryptoException("Alıcının anahtarı doğrulanamadı: sunucu araya sahte bir anahtar koymuş olabilir");
            }
            return signContact(own, ownPhone, recipientPhone,
                    UserKeys.fingerprint(recipientEncryptionKey, recipientSigningKey));
        } finally {
            Arrays.fill(macKey, (byte) 0);
        }
    }

    // ---------- Kişi kaydı imzası ----------

    public static String signContact(UserKeys own, String ownPhone, String contactPhone, String fingerprint) {
        return Base64.getEncoder().encodeToString(
                Signatures.sign(own.signing().getPrivate(), contactRecord(ownPhone, contactPhone, fingerprint)));
    }

    /** Kayıt sunucuda durur; istemci kendi imzası tutmayan kaydı kabul etmez (Crypto_Design §7.7). */
    public static boolean contactSignatureValid(UserKeys own, String ownPhone, String contactPhone, String fingerprint,
                                                String signature) {
        if (fingerprint == null || signature == null) {
            return false;
        }
        try {
            return Signatures.verify(own.signing().getPublic(), contactRecord(ownPhone, contactPhone, fingerprint),
                    Base64.getDecoder().decode(signature));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ---------- Yardımcılar ----------

    static byte[] derive(String code, String info) {
        try {
            KDF hkdf = KDF.getInstance("HKDF-SHA256");
            return hkdf.deriveData(HKDFParameterSpec.ofExtract()
                    .addIKM(code.getBytes(StandardCharsets.US_ASCII))
                    .thenExpand(Aead.utf8(info), KEY_LENGTH));
        } catch (GeneralSecurityException e) {
            throw new CryptoException("HKDF kullanılamıyor", e);
        }
    }

    private static byte[] seal(byte[] macKey, byte[] senderFingerprint, byte[] recipientFingerprint) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            mac.update(Aead.utf8("gps-invite-accept/v1"));
            mac.update(senderFingerprint);
            return mac.doFinal(recipientFingerprint);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Kabul mührü hesaplanamadı", e);
        }
    }

    private static byte[] selfBinding(String verifierHash) {
        return Aead.utf8("gps-invite-self/v1|" + verifierHash);
    }

    private static byte[] contactRecord(String ownPhone, String contactPhone, String fingerprint) {
        return Aead.utf8("gps-contact/v1|" + ownPhone + "|" + contactPhone + "|" + fingerprint);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
