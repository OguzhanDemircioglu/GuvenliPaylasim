package com.server.modules.auth;

import com.server.common.exception.LoginBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Brute-force koruması: deneme parola kontrolünden önce sayılır, sınırı aşan deneme reddedilir, başarılı denemede
 * sayaç silinir. Sayımın eş zamanlı isteklerde doğru çalıştığı gerçek PostgreSQL ile ConcurrencyAcceptanceTest'te.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    private static final String KEY = "10.0.0.1_+905551234567";
    private static final Duration BLOCK = Duration.ofHours(1);

    @Mock
    private LoginAttemptRepository repository;

    private LoginAttemptServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptServiceImpl(repository, new LoginProtectionProperties(5, BLOCK, 30));
    }

    @Test
    void attemptsUpToTheLimitAreAllowed() {
        when(repository.countAttempt(eq(KEY), any(), any(), eq(5))).thenReturn(1, 5);

        assertThatCode(() -> service.countAttempt(KEY)).doesNotThrowAnyException();
        assertThatCode(() -> service.countAttempt(KEY)).doesNotThrowAnyException();
    }

    @Test
    void attemptOverTheLimitIsRejected() {
        when(repository.countAttempt(eq(KEY), any(), any(), eq(5))).thenReturn(6);

        assertThatThrownBy(() -> service.countAttempt(KEY))
                .isInstanceOf(LoginBlockedException.class)
                .hasMessage(AuthMessages.LOGIN_BLOCKED);
    }

    @Test
    void attemptIsCountedWithBlockDuration() {
        Instant before = Instant.now();
        when(repository.countAttempt(eq(KEY), any(), any(), anyInt())).thenReturn(1);

        service.countAttempt(KEY);

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> blockedUntil = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countAttempt(eq(KEY), now.capture(), blockedUntil.capture(), eq(5));
        assertThat(now.getValue()).isAfterOrEqualTo(before);
        assertThat(blockedUntil.getValue()).isEqualTo(now.getValue().plus(BLOCK));
    }

    @Test
    void numberWideAttemptsUseTheHigherLimit() {
        when(repository.countAttempt(eq("phone_+905551234567"), any(), any(), eq(30))).thenReturn(30, 31);

        assertThatCode(() -> service.countPhoneAttempt("+905551234567")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.countPhoneAttempt("+905551234567")).isInstanceOf(LoginBlockedException.class);
    }

    @Test
    void successfulLoginClearsCounter() {
        service.clear(KEY);

        verify(repository).deleteById(KEY);
    }
}
