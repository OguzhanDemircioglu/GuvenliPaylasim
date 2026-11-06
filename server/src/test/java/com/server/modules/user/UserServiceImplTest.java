package com.server.modules.user;

import com.server.common.exception.BusinessException;
import com.server.modules.auth.dto.RegisterRequest;
import com.server.modules.key.KeyService;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.verification.VerificationMessages;
import com.server.modules.verification.VerificationPurpose;
import com.server.modules.verification.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** v2 kaydı (Crypto_Design §7.2): kod doğrulanmadan hesap açılmaz; hesap ve anahtarlar birlikte kaydedilir. */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final KeyBundleRequest KEYS = new KeyBundleRequest("enc", "sig", "bundle");

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private VerificationService verificationService;
    @Mock
    private KeyService keyService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, verificationService, keyService);
    }

    @Test
    void verifiedRegistrationStoresUserAndKeys() {
        when(userRepository.existsByPhone("+905551234567")).thenReturn(false);
        when(passwordEncoder.encode("auth-secret")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 5L);
            return user;
        });

        service.register(request("5551234567", "  Ayşe Yılmaz "));

        verify(verificationService).verify("+905551234567", VerificationPurpose.REGISTER, "123456");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo("+905551234567");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ayşe Yılmaz");
        assertThat(saved.getValue().getPassword()).isEqualTo("bcrypt-hash");
        verify(keyService).create(5L, KEYS);
    }

    @Test
    void wrongCodeCreatesNothing() {
        doThrow(new BusinessException(VerificationMessages.CODE_INVALID))
                .when(verificationService).verify("+905551234567", VerificationPurpose.REGISTER, "123456");

        assertThatThrownBy(() -> service.register(request("5551234567", "Ayşe")))
                .hasMessage(VerificationMessages.CODE_INVALID);
        verify(userRepository, never()).saveAndFlush(any());
        verify(keyService, never()).create(any(), any());
    }

    @Test
    void registeredPhoneCannotRegisterAgain() {
        when(userRepository.existsByPhone("+905551234567")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request("5551234567", "Ayşe")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.PHONE_EXISTS);
        verify(userRepository, never()).saveAndFlush(any());
    }

    private static RegisterRequest request(String phone, String fullName) {
        return new RegisterRequest(phone, "123456", fullName, "auth-secret", KEYS);
    }
}
