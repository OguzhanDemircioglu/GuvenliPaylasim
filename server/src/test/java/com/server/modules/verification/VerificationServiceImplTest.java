package com.server.modules.verification;

import com.server.common.exception.BusinessException;
import com.server.common.exception.LoginBlockedException;
import com.server.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crypto_Design §7.2 / §7.9, Security.md S4 ve S8: kayıtlı ve kayıtsız numara aynı işlemlerden geçer; kod sadece
 * uygun numaraya gider; kod 3 yanlış denemede ve süresi dolunca geçersizdir; sınırlar aşılınca 429.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

    private static final String PHONE = "+905551234567";
    private static final String IP = "10.0.0.1";

    @Mock
    private PhoneVerificationRepository verificationRepository;
    @Mock
    private VerificationRequestRepository requestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationMessageSender sender;

    private VerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VerificationServiceImpl(verificationRepository, requestRepository, userRepository, sender,
                new VerificationProperties(Duration.ofMinutes(5), 3, 3, 10, 200, false));
    }

    @Test
    void registerCodeGoesOnlyToUnregisteredNumberButBothAreRecordedTheSameWay() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        service.start(PHONE, VerificationPurpose.REGISTER, IP);
        verify(sender).enqueue(eq(PHONE), anyString());

        // Kayıtlı numara: aynı kayıtlar yazılır, mesaj gitmez, istisna yok (cevap aynı).
        when(userRepository.existsByPhone("+905559999999")).thenReturn(true);
        service.start("+905559999999", VerificationPurpose.REGISTER, IP);
        verify(sender, never()).enqueue(eq("+905559999999"), anyString());
        verify(requestRepository, times(2)).save(any(VerificationRequest.class));
        verify(verificationRepository, times(2)).save(any(PhoneVerification.class));
    }

    @Test
    void normalModeNeverReturnsTheCode() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        assertThat(service.start(PHONE, VerificationPurpose.REGISTER, IP)).isEmpty();
    }

    @Test
    void testModeReturnsTheRegistrationCodeAndSendsNothing() {
        VerificationServiceImpl testMode = new VerificationServiceImpl(verificationRepository, requestRepository,
                userRepository, sender, new VerificationProperties(Duration.ofMinutes(5), 3, 3, 10, 200, true));

        String code = testMode.start(PHONE, VerificationPurpose.REGISTER, IP).orElseThrow();

        verify(sender, never()).enqueue(anyString(), anyString());
        assertThat(code).matches("\\d{6}");
        ArgumentCaptor<PhoneVerification> saved = ArgumentCaptor.forClass(PhoneVerification.class);
        verify(verificationRepository).save(saved.capture());
        assertThat(VerificationCodes.matches(saved.getValue().getCodeHash(), PHONE, VerificationPurpose.REGISTER, code))
                .isTrue();
    }

    @Test
    void testModeReturnsThePasswordResetCodeAndSendsNothing() {
        VerificationServiceImpl testMode = new VerificationServiceImpl(verificationRepository, requestRepository,
                userRepository, sender, new VerificationProperties(Duration.ofMinutes(5), 3, 3, 10, 200, true));

        assertThat(testMode.start(PHONE, VerificationPurpose.PASSWORD_RESET, IP)).hasValueSatisfying(
                code -> assertThat(code).hasSize(6));
        verify(sender, never()).enqueue(anyString(), anyString());
    }

    @Test
    void resetCodeGoesOnlyToRegisteredNumber() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(true);
        service.start(PHONE, VerificationPurpose.PASSWORD_RESET, IP);
        verify(sender).enqueue(eq(PHONE), anyString());

        when(userRepository.existsByPhone("+905559999999")).thenReturn(false);
        service.start("+905559999999", VerificationPurpose.PASSWORD_RESET, IP);
        verify(sender, never()).enqueue(eq("+905559999999"), anyString());
    }

    @Test
    void storedHashMatchesTheSentCodeAndTheCodeIsNotStoredInClear() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        service.start(PHONE, VerificationPurpose.REGISTER, IP);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender).enqueue(eq(PHONE), message.capture());
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(message.getValue());
        assertThat(matcher.find()).isTrue();
        String code = matcher.group(1);

        ArgumentCaptor<PhoneVerification> saved = ArgumentCaptor.forClass(PhoneVerification.class);
        verify(verificationRepository).save(saved.capture());
        assertThat(saved.getValue().getCodeHash()).isNotEqualTo(code).hasSize(64);
        assertThat(VerificationCodes.matches(saved.getValue().getCodeHash(), PHONE, VerificationPurpose.REGISTER, code))
                .isTrue();
        // Kayıt kodu sıfırlama için geçerli değildir.
        assertThat(VerificationCodes.matches(saved.getValue().getCodeHash(), PHONE,
                VerificationPurpose.PASSWORD_RESET, code)).isFalse();
        assertThat(saved.getValue().getExpiresAt()).isBetween(Instant.now().plusSeconds(290), Instant.now().plusSeconds(301));
    }

    @Test
    void perPhoneLimitBlocksWithoutWritingAnything() {
        when(requestRepository.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.start(PHONE, VerificationPurpose.REGISTER, IP))
                .isInstanceOf(LoginBlockedException.class)
                .hasMessage(VerificationMessages.RATE_LIMITED);
        verify(requestRepository, never()).save(any());
        verify(sender, never()).enqueue(anyString(), anyString());
    }

    @Test
    void perIpLimitBlocks() {
        when(requestRepository.countByIpAndCreatedAtAfter(eq(IP), any())).thenReturn(10L);

        assertThatThrownBy(() -> service.start(PHONE, VerificationPurpose.REGISTER, IP))
                .hasMessage(VerificationMessages.RATE_LIMITED);
    }

    @Test
    void dailyLimitStopsAllSending() {
        when(requestRepository.countByCreatedAtAfter(any())).thenReturn(200L);

        assertThatThrownBy(() -> service.start(PHONE, VerificationPurpose.PASSWORD_RESET, IP))
                .hasMessage(VerificationMessages.RATE_LIMITED);
        verify(sender, never()).enqueue(anyString(), anyString());
    }

    @Test
    void correctCodeIsConsumed() {
        PhoneVerification verification = verification("123456", Instant.now().plusSeconds(60));
        when(verificationRepository.findByPhoneAndPurpose(PHONE, VerificationPurpose.REGISTER))
                .thenReturn(Optional.of(verification));

        service.verify(PHONE, VerificationPurpose.REGISTER, "123456");

        verify(verificationRepository).delete(verification);
    }

    @Test
    void wrongCodeCountsAndThirdMistakeDeletesTheCode() {
        PhoneVerification verification = verification("123456", Instant.now().plusSeconds(60));
        when(verificationRepository.findByPhoneAndPurpose(PHONE, VerificationPurpose.REGISTER))
                .thenReturn(Optional.of(verification));

        for (int i = 1; i <= 2; i++) {
            assertInvalid(() -> service.verify(PHONE, VerificationPurpose.REGISTER, "000000"));
            assertThat(verification.getAttempts()).isEqualTo(i);
        }
        verify(verificationRepository, never()).delete(any());

        assertInvalid(() -> service.verify(PHONE, VerificationPurpose.REGISTER, "000000"));
        verify(verificationRepository).delete(verification);
    }

    @Test
    void expiredOrMissingCodeGetsTheSameAnswer() {
        PhoneVerification expired = verification("123456", Instant.now().minusSeconds(1));
        when(verificationRepository.findByPhoneAndPurpose(PHONE, VerificationPurpose.REGISTER))
                .thenReturn(Optional.of(expired));
        assertInvalid(() -> service.verify(PHONE, VerificationPurpose.REGISTER, "123456"));
        verify(verificationRepository).delete(expired);

        when(verificationRepository.findByPhoneAndPurpose(PHONE, VerificationPurpose.PASSWORD_RESET))
                .thenReturn(Optional.empty());
        assertInvalid(() -> service.verify(PHONE, VerificationPurpose.PASSWORD_RESET, "123456"));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).hasMessage(VerificationMessages.CODE_INVALID);
    }

    private static PhoneVerification verification(String code, Instant expiresAt) {
        PhoneVerification verification = new PhoneVerification();
        verification.setPhone(PHONE);
        verification.setPurpose(VerificationPurpose.REGISTER);
        verification.setCodeHash(VerificationCodes.hash(PHONE, VerificationPurpose.REGISTER, code));
        verification.setExpiresAt(expiresAt);
        verification.setCreatedAt(Instant.now());
        return verification;
    }
}
