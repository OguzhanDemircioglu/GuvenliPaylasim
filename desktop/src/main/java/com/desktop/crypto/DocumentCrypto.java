package com.desktop.crypto;

import javax.crypto.AEADBadTagException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hibrit şifreleme (docs/Crypto_Design.md §4):
 * <ol>
 *   <li>Belgeye özel rastgele 256 bitlik anahtar üretilir.</li>
 *   <li>İçerik bu anahtarla AES-256-GCM ile şifrelenir.</li>
 *   <li>Şifreli içerik gönderenin Ed25519 anahtarıyla imzalanır.</li>
 *   <li>Belge anahtarı her alıcının X25519 açık anahtarına ayrı ayrı sarmalanır.</li>
 * </ol>
 * Sunucuya sadece şifreli içerik, imza ve sarmalanmış anahtarlar gider.
 */
public final class DocumentCrypto {

    private static final byte PAYLOAD_VERSION = 1;
    private static final byte[] PAYLOAD_AAD = Aead.utf8("gps-doc/v1");
    private static final byte[] SIGNATURE_CONTEXT = Aead.utf8("gps-doc-sig/v1|");

    private DocumentCrypto() {
    }

    /** Belgeyi alabilecek kişi: kullanıcı id'si ve X25519 açık anahtarı. */
    public record Recipient(long userId, String encryptionPublicKey) {
    }

    /** {@code wrappedKeys}: alıcı id'si → Base64 sarmalanmış belge anahtarı. */
    public record Sealed(byte[] payload, String signature, Map<Long, String> wrappedKeys) {
    }

    public enum SignatureStatus {
        /** İmza gönderenin kayıtlı anahtarıyla doğrulandı. */
        VERIFIED,
        /** Gönderenin anahtarları sıfırlanmış; imza doğrulanamıyor. */
        UNKNOWN
    }

    public record Opened(DocumentContent content, SignatureStatus signature) {
    }

    public static Sealed seal(DocumentContent content, UserKeys sender, List<Recipient> recipients) {
        if (recipients.isEmpty()) {
            throw new CryptoException("En az bir alıcı seçilmelidir");
        }
        byte[] documentKey = Aead.randomKey();
        try {
            byte[] sealed = Aead.encrypt(documentKey, content.serialize(), PAYLOAD_AAD);
            byte[] payload = new byte[1 + sealed.length];
            payload[0] = PAYLOAD_VERSION;
            System.arraycopy(sealed, 0, payload, 1, sealed.length);

            String signature = Base64.getEncoder().encodeToString(
                    Signatures.sign(sender.signing().getPrivate(), signedBytes(payload)));

            byte[] binding = sha256(payload);
            Map<Long, String> wrapped = new LinkedHashMap<>();
            for (Recipient recipient : recipients) {
                byte[] key = KeyWrap.wrap(documentKey,
                        UserKeys.publicKey("X25519", recipient.encryptionPublicKey()), binding);
                wrapped.put(recipient.userId(), Base64.getEncoder().encodeToString(key));
            }
            return new Sealed(payload, signature, wrapped);
        } finally {
            Arrays.fill(documentKey, (byte) 0);
        }
    }

    /**
     * Önce imza doğrulanır: imza geçersizse belge değiştirilmiş ya da başkası tarafından üretilmiştir ve
     * içeriği hiç çözülmez.
     *
     * @param senderSigningPublicKey null ise gönderenin anahtarları sıfırlanmıştır; içerik açılır ama
     *                               {@link SignatureStatus#UNKNOWN} döner
     */
    public static Opened open(byte[] payload, String signature, String wrappedKey, String senderSigningPublicKey,
                              UserKeys recipient) {
        SignatureStatus status = SignatureStatus.UNKNOWN;
        if (senderSigningPublicKey != null) {
            boolean valid = Signatures.verify(UserKeys.publicKey("Ed25519", senderSigningPublicKey),
                    signedBytes(payload), Base64.getDecoder().decode(signature));
            if (!valid) {
                throw new CryptoException("İmza doğrulanamadı: belge değiştirilmiş ya da gönderen bu kişi değil");
            }
            status = SignatureStatus.VERIFIED;
        }
        if (payload.length < 2 || payload[0] != PAYLOAD_VERSION) {
            throw new CryptoException("Belge biçimi tanınmıyor");
        }
        byte[] documentKey = KeyWrap.unwrap(Base64.getDecoder().decode(wrappedKey), recipient.encryption(),
                sha256(payload));
        try {
            byte[] plain = Aead.decrypt(documentKey, Arrays.copyOfRange(payload, 1, payload.length), PAYLOAD_AAD);
            return new Opened(DocumentContent.deserialize(plain), status);
        } catch (AEADBadTagException e) {
            throw new CryptoException("Belge değiştirilmiş; içerik açılmadı");
        } finally {
            Arrays.fill(documentKey, (byte) 0);
        }
    }

    private static byte[] signedBytes(byte[] payload) {
        byte[] data = new byte[SIGNATURE_CONTEXT.length + payload.length];
        System.arraycopy(SIGNATURE_CONTEXT, 0, data, 0, SIGNATURE_CONTEXT.length);
        System.arraycopy(payload, 0, data, SIGNATURE_CONTEXT.length, payload.length);
        return data;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("SHA-256 kullanılamıyor", e);
        }
    }
}
