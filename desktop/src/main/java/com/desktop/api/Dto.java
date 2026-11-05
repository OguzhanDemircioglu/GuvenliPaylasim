package com.desktop.api;

import java.time.Instant;
import java.util.List;

/** Sunucu sözleşmesinin istemci tarafı. Alan adları sunucudaki DTO'larla birebir aynıdır. */
public final class Dto {

    private Dto() {
    }

    public enum Role { SUPERADMIN, ADMIN, OWNER, USER }

    public enum DocumentKind { TEXT, FILE }

    public record SessionUser(Long id, String email, String fullName, Role role, Long unitId,
                              boolean mustChangePassword) {
        public boolean isAdmin() {
            return role == Role.ADMIN || role == Role.SUPERADMIN;
        }
    }

    public record SessionInfo(String accessToken, long expiresIn, SessionUser user) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record KeyBundle(String encryptionPublicKey, String signingPublicKey, String privateKeyBundle) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword, KeyBundle keys) {
    }

    public record Unit(Long id, String name, String description, Long version) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record UnitRequest(String name, String description, Long version) {
    }

    public record User(Long id, String email, String fullName, Role role, boolean active, Long unitId,
                       String phone, boolean mustChangePassword, Long version) {
    }

    public record UserCreateRequest(String email, String fullName, String password, Role role, Long unitId,
                                    String phone) {
    }

    public record UserUpdateRequest(String email, String fullName, Role role, boolean active, Long unitId,
                                    String phone, Long version) {
    }

    public record PasswordResetRequest(String newPassword) {
    }

    public record Recipient(Long userId, String fullName, Long unitId, String encryptionPublicKey,
                            String signingPublicKey) {
    }

    public record RecipientKey(Long userId, String wrappedKey) {
    }

    public record DocumentSendRequest(DocumentKind kind, String payload, String signature,
                                      List<RecipientKey> recipients) {
    }

    public record DocumentSent(Long id, int recipientCount, int smsNotified) {
    }

    public record InboxItem(Long id, DocumentKind kind, long sizeBytes, Long senderId, String senderName,
                            Long senderUnitId, Instant createdAt, Instant readAt) {
    }

    public record DocumentPayload(Long id, DocumentKind kind, String payload, String signature, String wrappedKey,
                                  Long senderId, String senderName, Long senderUnitId, String senderSigningPublicKey,
                                  Instant createdAt) {
    }

    public record SentRecipient(Long documentId, Long userId, String fullName, Long unitId, Instant readAt,
                                Instant deletedAt) {
    }

    public record SentItem(Long id, DocumentKind kind, long sizeBytes, Instant createdAt, Instant purgedAt,
                           List<SentRecipient> recipients) {
    }
}
