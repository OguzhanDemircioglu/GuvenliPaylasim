package com.server.modules.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Bir tarayıcıdaki oturum. Refresh token her yenilendiğinde yeni satır açılmaz, bu satır güncellenir.
 * Teknik tablo: BaseEntity'den türemez, soft delete kullanmaz.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_sessions", indexes = {
        @Index(name = "ix_user_sessions_current_token", columnList = "current_token_hash"),
        @Index(name = "ix_user_sessions_previous_token", columnList = "previous_token_hash"),
        @Index(name = "ix_user_sessions_user", columnList = "user_id")
})
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "current_token_hash", nullable = false, length = 64)
    private String currentTokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    public static UserSession open(Long userId, String tokenHash, ClientInfo client, Instant now, Instant expiresAt) {
        UserSession session = new UserSession();
        session.userId = userId;
        session.currentTokenHash = tokenHash;
        session.expiresAt = expiresAt;
        session.userAgent = client.userAgent();
        session.ip = client.ip();
        session.createdAt = now;
        session.lastUsedAt = now;
        return session;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
