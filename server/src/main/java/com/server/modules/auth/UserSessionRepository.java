package com.server.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByCurrentTokenHashOrPreviousTokenHash(String currentTokenHash, String previousTokenHash);

    /**
     * Atomik rotation: sadece token hâlâ güncelse yeniler. Aynı anda gelen iki istekten yalnızca biri 1 döner.
     */
    @Transactional
    @Modifying
    @Query("""
            update UserSession s
               set s.previousTokenHash = s.currentTokenHash,
                   s.currentTokenHash = :newHash,
                   s.rotatedAt = :now,
                   s.lastUsedAt = :now,
                   s.expiresAt = :expiresAt
             where s.id = :id and s.currentTokenHash = :oldHash and s.revokedAt is null""")
    int rotate(Long id, String oldHash, String newHash, Instant now, Instant expiresAt);

    @Transactional
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.id = :id and s.revokedAt is null")
    int revoke(Long id, Instant now);

    @Transactional
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUserId(Long userId, Instant now);

    @Transactional
    @Modifying
    @Query("""
            update UserSession s set s.revokedAt = :now
             where s.userId = :userId and s.id <> :keepSessionId and s.revokedAt is null""")
    int revokeAllByUserIdExcept(Long userId, Long keepSessionId, Instant now);

    @Transactional
    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :now or s.revokedAt < :revokedBefore")
    int deleteExpiredOrRevoked(Instant now, Instant revokedBefore);
}
