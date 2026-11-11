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
    public void countAttempt(String attemptKey) {
        count(attemptKey, properties.maxAttempts());
    }

    @Override
    public void countPhoneAttempt(String phone) {
        count(LoginAttemptService.phoneKey(phone), properties.phoneMaxAttempts());
    }

    /** Sayı tek SQL komutunda artar ve okunur; eş zamanlı isteklerden sadece ilk {@code maxAttempts} tanesi geçer. */
    private void count(String attemptKey, int maxAttempts) {
        Instant now = Instant.now();
        if (repository.countAttempt(attemptKey, now, now.plus(properties.blockDuration()), maxAttempts) > maxAttempts) {
            throw new LoginBlockedException(AuthMessages.LOGIN_BLOCKED);
        }
    }

    @Override
    public void clear(String attemptKey) {
        repository.deleteById(attemptKey);
    }
}
