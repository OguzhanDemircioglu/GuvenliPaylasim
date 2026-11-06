package com.server.modules.auth;

import com.server.modules.verification.PhoneVerificationRepository;
import com.server.modules.verification.VerificationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Her gece eski login denemelerini, bitmiş oturumları, süresi dolmuş doğrulama kodlarını ve 1 günden eski kod
 * isteklerini siler.
 * Birden fazla replica varsa her birinde ayrı çalışır; sadece eski kayıt sildiği için zararsızdır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCleanupJob {

    private static final Duration LOGIN_ATTEMPT_RETENTION = Duration.ofDays(1);
    private static final Duration REVOKED_SESSION_RETENTION = Duration.ofDays(7);

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserSessionRepository sessionRepository;
    private final PhoneVerificationRepository verificationRepository;
    private final VerificationRequestRepository verificationRequestRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Istanbul")
    public void cleanup() {
        Instant now = Instant.now();
        int attempts = loginAttemptRepository.deleteOlderThan(now.minus(LOGIN_ATTEMPT_RETENTION));
        int sessions = sessionRepository.deleteExpiredOrRevoked(now, now.minus(REVOKED_SESSION_RETENTION));
        int codes = verificationRepository.deleteExpired(now);
        int requests = verificationRequestRepository.deleteOlderThan(now.minus(LOGIN_ATTEMPT_RETENTION));
        log.info("Auth cleanup: {} login attempts, {} sessions, {} codes, {} code requests deleted",
                attempts, sessions, codes, requests);
    }
}
