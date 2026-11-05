package com.server.modules.auth;

import com.server.common.exception.LoginBlockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final LoginAttemptRepository repository;
    private final LoginProtectionProperties properties;

    @Override
    public void ensureNotBlocked(String attemptKey) {
        Instant now = Instant.now();
        if (repository.findById(attemptKey).filter(attempt -> attempt.isBlocked(now)).isPresent()) {
            throw new LoginBlockedException(AuthMessages.LOGIN_BLOCKED);
        }
    }

    @Override
    public void recordFailure(String attemptKey) {
        Instant now = Instant.now();
        repository.recordFailure(attemptKey, now, now.plus(properties.blockDuration()), properties.maxAttempts());
    }

    @Override
    public void clear(String attemptKey) {
        repository.deleteById(attemptKey);
    }
}
