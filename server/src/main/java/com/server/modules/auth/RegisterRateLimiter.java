package com.server.modules.auth;

import com.server.common.exception.LoginBlockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Herkese açık kaydı IP başına sınırlar. Login denemeleriyle aynı tabloyu ve atomik sayacı kullanır (anahtar: register_IP).
 * Başarılı ya da başarısız her deneme sayılır: böylece kayıtlı e-posta taraması da sınırlanır.
 */
@Component
@RequiredArgsConstructor
public class RegisterRateLimiter {

    private static final String KEY_PREFIX = "register_";

    private final LoginAttemptRepository repository;
    private final RegisterProtectionProperties properties;

    /** Engelliyse 429 fırlatır, değilse denemeyi sayar. */
    public void acquire(String ip) {
        String key = KEY_PREFIX + ip;
        Instant now = Instant.now();
        if (repository.findById(key).filter(attempt -> attempt.isBlocked(now)).isPresent()) {
            throw new LoginBlockedException(AuthMessages.REGISTER_BLOCKED);
        }
        repository.recordFailure(key, now, now.plus(properties.blockDuration()), properties.maxAttempts());
    }
}
