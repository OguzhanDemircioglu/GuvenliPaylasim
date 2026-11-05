package com.server.modules.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Yanlış login denemeleri. Anahtar: IP_email. Teknik tablo: BaseEntity'den türemez.
 * Kayıtlar atomik bir upsert ile yazılır (LoginAttemptRepository.recordFailure).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @Column(name = "attempt_key", length = 300)
    private String attemptKey;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isBlocked(Instant now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }
}
