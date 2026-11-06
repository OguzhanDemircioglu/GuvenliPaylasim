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

    /** {@code lifetimeHours}: 1, 24, 168 ya da 720 (Crypto_Design §8.2). */
    public record DocumentSendRequest(DocumentKind kind, String payload, String signature,
                                      List<RecipientKey> recipients, int lifetimeHours, boolean deleteOnOpen) {
    }

    public record DocumentSent(Long id, int recipientCount) {
    }

    public record InboxItem(Long id, DocumentKind kind, long sizeBytes, Long senderId, String senderName,
                            Instant createdAt, Instant expiresAt, boolean deleteOnOpen, Instant readAt) {
    }

    public record DocumentPayload(Long id, DocumentKind kind, String payload, String signature, String wrappedKey,
                                  Long senderId, String senderName, String senderSigningPublicKey,
                                  Instant createdAt, Instant expiresAt, boolean deleteOnOpen) {
    }

    public record SentRecipient(Long documentId, Long userId, String fullName, Instant readAt) {
    }

    public record SentItem(Long id, DocumentKind kind, long sizeBytes, Instant createdAt, Instant expiresAt,
                           boolean deleteOnOpen, List<SentRecipient> recipients) {
    }

    // ---------- Kişiler ve davet kodu (Crypto_Design §7.6–7.8) ----------

    public enum ContactStatus { VERIFIED, PENDING }

    /** {@code fingerprint}/{@code signature}: kayıttaki doğrulanmış değer; anahtarlar kişinin güncel anahtarlarıdır. */
    public record Contact(Long userId, String fullName, String phone, ContactStatus status, String fingerprint,
                          String signature, String encryptionPublicKey, String signingPublicKey) {
    }

    public record InviteCreateRequest(String recipientPhone, int ttlHours, DocumentKind kind, String payload,
                                      String signature, String wrapped, String verifierHash, String selfWrapped,
                                      int lifetimeHours, boolean deleteOnOpen) {
    }

    public record InviteCreated(Long id, Instant expiresAt) {
    }

    public record InviteRedeemRequest(String verifier) {
    }

    public record Party(Long userId, String fullName, String phone, String encryptionPublicKey,
                        String signingPublicKey) {
    }

    public record InviteRedeem(Long id, DocumentKind kind, String payload, String signature, String wrapped,
                               Instant createdAt, Party sender) {
    }

    public record InviteAcceptRequest(String wrappedKey, String seal, String fingerprint, String signature) {
    }

    public record InviteAccepted(Long documentId) {
    }

    public record InviteVerification(Long id, String selfWrapped, String verifierHash, String seal, Party recipient) {
    }

    public record InviteVerifyRequest(String fingerprint, String signature) {
    }

    public record SentInvite(Long id, String recipientPhone, DocumentKind kind, long sizeBytes, Instant createdAt,
                             Instant expiresAt) {
    }
}
