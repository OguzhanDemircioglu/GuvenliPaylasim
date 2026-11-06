package com.server.modules.totp;

import com.server.common.exception.BusinessException;
import com.server.modules.auth.AuthMessages;
import com.server.modules.auth.LoginAttemptRepository;
import com.server.modules.auth.LoginProtectionProperties;
import com.server.modules.totp.dto.TotpEnabledResponse;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Crypto_Design §7.4: açma, kapatma, tek kullanımlık kodlar ve kurtarma kodları. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TotpServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RecoveryCodeRepository recoveryCodeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptRepository attemptRepository;

    private TotpServiceImpl service;
    private User user;
    private final List<RecoveryCode> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new TotpServiceImpl(userRepository, recoveryCodeRepository, passwordEncoder, attemptRepository,
                new LoginProtectionProperties(5, Duration.ofHours(1)));
        user = new User();
        user.setId(USER_ID);
        user.setPhone("+905551234567");
        user.setPassword("hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(recoveryCodeRepository.save(any(RecoveryCode.class))).thenAnswer(i -> {
            stored.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(recoveryCodeRepository.findByUserIdAndCodeHash(eq(USER_ID), anyString())).thenAnswer(i -> stored.stream()
                .filter(c -> c.getCodeHash().equals(i.getArgument(1))).findFirst());
    }

    @Test
    void setupThenEnableWithTheCurrentCodeGivesTenRecoveryCodes() {
        var setup = service.setup(USER_ID);
        assertThat(setup.uri()).startsWith("otpauth://totp/").contains("secret=" + setup.secret());
        assertThat(user.isTotpEnabled()).isFalse();

        TotpEnabledResponse enabled = service.enable(USER_ID, currentCode(setup.secret()));

        assertThat(user.isTotpEnabled()).isTrue();
        assertThat(user.getTotpSecret()).isEqualTo(setup.secret());
        assertThat(user.getTotpPendingSecret()).isNull();
        assertThat(enabled.recoveryCodes()).hasSize(10).allMatch(c -> c.matches("[0-9A-Z]{5}-[0-9A-Z]{5}"))
                .doesNotHaveDuplicates();
        assertThat(stored).hasSize(10).noneMatch(c -> enabled.recoveryCodes().contains(c.getCodeHash()));
    }

    @Test
    void wrongFirstCodeDoesNotEnableAndIsCounted() {
        service.setup(USER_ID);

        assertThatThrownBy(() -> service.enable(USER_ID, "000000")).hasMessage(TotpMessages.CODE_INVALID);
        assertThat(user.isTotpEnabled()).isFalse();
        verify(attemptRepository).recordFailure(eq("totp_" + USER_ID), any(), any(), eq(5));
    }

    @Test
    void sameAuthenticatorCodeWorksOnlyOnce() {
        String secret = enable();
        // Etkinleştirmede kullanılan kod tekrar geçmez; sonraki adımın kodu geçer, o da bir kez.
        String now = currentCode(secret);
        assertThat(service.verify(USER_ID, now)).isFalse();
        String next = Totp.code(Totp.fromBase32(secret), Totp.currentStep() + 1);
        assertThat(service.verify(USER_ID, next)).isTrue();
        assertThat(service.verify(USER_ID, next)).isFalse();
    }

    @Test
    void recoveryCodeWorksOnceAndIsForgivingToType() {
        enable();
        String code = lastRecoveryCodes.getFirst();
        when(recoveryCodeRepository.findByUserIdAndCodeHash(eq(USER_ID), anyString())).thenAnswer(i -> stored.stream()
                .filter(c -> c.getCodeHash().equals(i.getArgument(1))).findFirst());
        org.mockito.Mockito.doAnswer(i -> stored.remove((RecoveryCode) i.getArgument(0)))
                .when(recoveryCodeRepository).delete(any(RecoveryCode.class));

        assertThat(service.verify(USER_ID, " " + code.toLowerCase().replace("-", " ") + " ")).isTrue();
        assertThat(service.verify(USER_ID, code)).isFalse();
        assertThat(stored).hasSize(9);
    }

    @Test
    void disableNeedsThePasswordAndAValidCode() {
        String secret = enable();
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.disable(USER_ID, "wrong", currentCode(secret)))
                .hasMessage(AuthMessages.CURRENT_PASSWORD_INCORRECT);
        assertThatThrownBy(() -> service.disable(USER_ID, "right", "123456"))
                .hasMessage(TotpMessages.CODE_INVALID);
        assertThat(user.isTotpEnabled()).isTrue();

        service.disable(USER_ID, "right", Totp.code(Totp.fromBase32(secret), Totp.currentStep() + 1));
        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
        verify(recoveryCodeRepository, org.mockito.Mockito.atLeastOnce()).deleteByUserId(USER_ID);
    }

    @Test
    void verifyIsFalseWhenTotpIsOff() {
        assertThat(service.verify(USER_ID, "123456")).isFalse();
        verify(recoveryCodeRepository, never()).delete(any());
    }

    @Test
    void cannotSetupTwice() {
        enable();
        assertThatThrownBy(() -> service.setup(USER_ID)).isInstanceOf(BusinessException.class)
                .hasMessage(TotpMessages.ALREADY_ENABLED);
    }

    private List<String> lastRecoveryCodes = List.of();

    private String enable() {
        var setup = service.setup(USER_ID);
        lastRecoveryCodes = service.enable(USER_ID, currentCode(setup.secret())).recoveryCodes();
        return setup.secret();
    }

    private static String currentCode(String base32Secret) {
        return Totp.code(Totp.fromBase32(base32Secret), Totp.currentStep());
    }
}
