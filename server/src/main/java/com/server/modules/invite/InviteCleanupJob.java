package com.server.modules.invite;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Süresi dolan davetler içerikleriyle birlikte kalıcı olarak silinir (Crypto_Design §7.6). Davet ömrü en az 1 saat
 * olduğu için 10 dakikada bir çalışmak yeterlidir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteCleanupJob {

    private final InviteRepository repository;

    @Scheduled(fixedDelay = 10, initialDelay = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void cleanup() {
        Instant now = Instant.now();
        int deleted = repository.deleteFinished(now, now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Invite cleanup: {} invites deleted", deleted);
        }
    }
}
