package com.server.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    /**
     * Yanlış denemeyi atomik olarak sayar. Engel süresi dolmuşsa sayaç 1'den başlar;
     * sayaç maxAttempts'e ulaşınca blocked_until yazılır.
     * İki adımdır çünkü H2 "ON CONFLICT DO UPDATE" desteklemez; UPDATE satırı kilitlediği için sayım yine ezilmez.
     */
    @Transactional
    default void recordFailure(String key, Instant now, Instant blockedUntil, int maxAttempts) {
        insertIfMissing(key, now);
        incrementFailure(key, now, blockedUntil, maxAttempts);
    }

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO login_attempts (attempt_key, fail_count, blocked_until, updated_at)
            VALUES (:key, 0, NULL, :now)
            ON CONFLICT DO NOTHING""")
    void insertIfMissing(String key, Instant now);

    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE login_attempts SET
                fail_count = CASE
                    WHEN blocked_until <= :now THEN 1
                    ELSE fail_count + 1 END,
                blocked_until = CASE
                    WHEN blocked_until <= :now THEN NULL
                    WHEN fail_count + 1 >= :maxAttempts THEN :blockedUntil
                    ELSE blocked_until END,
                updated_at = :now
             WHERE attempt_key = :key""")
    void incrementFailure(String key, Instant now, Instant blockedUntil, int maxAttempts);

    @Transactional
    @Modifying
    @Query("delete from LoginAttempt a where a.updatedAt < :before")
    int deleteOlderThan(Instant before);
}
