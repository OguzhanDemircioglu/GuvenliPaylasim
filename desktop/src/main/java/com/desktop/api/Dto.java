package com.desktop.api;

import java.time.Instant;
import java.util.List;

/** Sunucu sözleşmesinin istemci tarafı. Alan adları sunucudaki DTO'larla birebir aynıdır. */
public final class Dto {

    private Dto() {
    }

    public enum DocumentKind { TEXT, FILE }

    /** {@code phone} E.164 ({@code +905XXXXXXXXX}); parola türetmede tuz olarak kullanılır. */
    public record SessionUser(Long id, String phone, String fullName) {
    }

    public record SessionInfo(String accessToken, long expiresIn, SessionUser user) {
    }

    /** {@code phone}: {@code 5XXXXXXXXX}. */
    public record LoginRequest(String phone, String password) {
    }

    public record KeyBundle(String encryptionPublicKey, String signingPublicKey, String privateKeyBundle) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword, KeyBundle keys) {
    }

    public record PhoneRequest(String phone) {
    }

    public record RegisterRequest(String phone, String code, String fullName, String password, KeyBundle keys) {
    }

    public record PasswordResetRequest(String phone, String code, String password, KeyBundle keys) {
    }

    /** Belge gönderilebilecek bir kişi ve açık anahtarları (kişiler 4. adımda gelir). */
    public record Recipient(Long userId, String fullName, String encryptionPublicKey, String signingPublicKey) {
    }

    public record RecipientKey(Long userId, String wrappedKey) {
    }

    public record DocumentSendRequest(DocumentKind kind, String payload, String signature,
                                      List<RecipientKey> recipients) {
    }

    public record DocumentSent(Long id, int recipientCount) {
    }

    public record InboxItem(Long id, DocumentKind kind, long sizeBytes, Long senderId, String senderName,
                            Instant createdAt, Instant readAt) {
    }

    public record DocumentPayload(Long id, DocumentKind kind, String payload, String signature, String wrappedKey,
                                  Long senderId, String senderName, String senderSigningPublicKey,
                                  Instant createdAt) {
    }

    public record SentRecipient(Long documentId, Long userId, String fullName, Instant readAt, Instant deletedAt) {
    }

    public record SentItem(Long id, DocumentKind kind, long sizeBytes, Instant createdAt, Instant purgedAt,
                           List<SentRecipient> recipients) {
    }
}
