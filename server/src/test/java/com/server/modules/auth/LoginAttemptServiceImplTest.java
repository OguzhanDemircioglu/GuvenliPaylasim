package com.server.modules.auth;

import com.server.common.exception.LoginBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Brute-force koruması: engel kontrolü, yanlış denemenin süre ve sınırla kaydedilmesi, başarılı girişte temizleme.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    private static final String KEY = "10.0.0.1_ayse.yilmaz@gps.local";
    private static final Duration BLOCK = Duration.ofHours(1);

    @Mock
    private LoginAttemptRepository repository;

    private LoginAttemptServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptServiceImpl(repository, new LoginProtectionProperties(5, BLOCK));
    }

    @Test
    void activeBlockRejectsLogin() {
        when(repository.findById(KEY)).thenReturn(Optional.of(attempt(Instant.now().plusSeconds(600))));

        assertThatThrownBy(() -> service.ensureNotBlocked(KEY))
                .isInstanceOf(LoginBlockedException.class)
                .hasMessage(AuthMessages.LOGIN_BLOCKED);
    }

    @Test
    void expiredBlockOrNoRecordAllowsLogin() {
        when(repository.findById(KEY)).thenReturn(Optional.of(attempt(Instant.now().minusSeconds(1))));
        assertThatCode(() -> service.ensureNotBlocked(KEY)).doesNotThrowAnyException();

        when(repository.findById(KEY)).thenReturn(Optional.of(attempt(null)));
        assertThatCode(() -> service.ensureNotBlocked(KEY)).doesNotThrowAnyException();

        when(repository.findById(KEY)).thenReturn(Optional.empty());
        assertThatCode(() -> service.ensureNotBlocked(KEY)).doesNotThrowAnyException();
    }

    @Test
    void failureIsRecordedWithBlockDurationAndLimit() {
        Instant before = Instant.now();

        service.recordFailure(KEY);

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> blockedUntil = ArgumentCaptor.forClass(Instant.class);
        verify(repository).recordFailure(eq(KEY), now.capture(), blockedUntil.capture(), eq(5));
        assertThat(now.getValue()).isAfterOrEqualTo(before);
        assertThat(blockedUntil.getValue()).isEqualTo(now.getValue().plus(BLOCK));
    }

    @Test
    void successfulLoginClearsCounter() {
        service.clear(KEY);

        verify(repository).deleteById(KEY);
    }

    private static LoginAttempt attempt(Instant blockedUntil) {
        LoginAttempt attempt = BeanUtils.instantiateClass(LoginAttempt.class);
        ReflectionTestUtils.setField(attempt, "attemptKey", KEY);
        ReflectionTestUtils.setField(attempt, "failCount", 5);
        ReflectionTestUtils.setField(attempt, "blockedUntil", blockedUntil);
        return attempt;
    }
}
