package com.server.modules.auth;

import com.server.common.exception.LoginBlockedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterRateLimiterTest {

    private static final String KEY = "register_1.2.3.4";

    @Mock
    private LoginAttemptRepository repository;

    private RegisterRateLimiter limiter() {
        return new RegisterRateLimiter(repository, new RegisterProtectionProperties(10, Duration.ofHours(1)));
    }

    @Test
    void countsAttemptWhenNotBlocked() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());

        limiter().acquire("1.2.3.4");

        verify(repository).recordFailure(eq(KEY), any(), any(), eq(10));
    }

    @Test
    void blockedIpGets429AndIsNotCountedAgain() {
        LoginAttempt attempt = mock(LoginAttempt.class);
        when(attempt.isBlocked(any())).thenReturn(true);
        when(repository.findById(KEY)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> limiter().acquire("1.2.3.4"))
                .isInstanceOf(LoginBlockedException.class)
                .hasMessage(AuthMessages.REGISTER_BLOCKED);

        verify(repository, never()).recordFailure(any(), any(), any(), eq(10));
    }
}
