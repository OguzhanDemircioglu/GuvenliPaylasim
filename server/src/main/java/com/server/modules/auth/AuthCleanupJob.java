package com.server.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Her gece eski login ve kayıt denemelerini, bitmiş oturumları siler.
 * Swarm'da her replica'da ayrı çalışır; sadece eski kayıt sildiği için zararsızdır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCleanupJob {

    private static final Duration LOGIN_ATTEMPT_RETENTION = Duration.ofDays(1);
    private static final Duration REVOKED_SESSION_RETENTION = Duration.ofDays(7);

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserSessionRepository sessionRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Istanbul")
    public void cleanup() {
        Instant now = Instant.now();
        int attempts = loginAttemptRepository.deleteOlderThan(now.minus(LOGIN_ATTEMPT_RETENTION));
        int sessions = sessionRepository.deleteExpiredOrRevoked(now, now.minus(REVOKED_SESSION_RETENTION));
        log.info("Auth cleanup: {} login attempts, {} sessions deleted", attempts, sessions);
    }
}
