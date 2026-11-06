package com.server.modules.user;

import com.server.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** v2 kaydı: telefon E.164 olarak saklanır, aynı numara iki kez kayıt olamaz. */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder);
    }

    @Test
    void registerStoresE164PhoneTrimmedNameAndHashedSecret() {
        when(userRepository.existsByPhone("+905551234567")).thenReturn(false);
        when(passwordEncoder.encode("auth-secret")).thenReturn("bcrypt-hash");

        service.register("5551234567", "  Ayşe Yılmaz ", "auth-secret");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPhone()).isEqualTo("+905551234567");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ayşe Yılmaz");
        assertThat(saved.getValue().getPassword()).isEqualTo("bcrypt-hash");
    }

    @Test
    void registeredPhoneCannotRegisterAgain() {
        when(userRepository.existsByPhone("+905551234567")).thenReturn(true);

        assertThatThrownBy(() -> service.register("5551234567", "Ayşe", "auth-secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.PHONE_EXISTS);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidPhoneNeverReachesTheDatabase() {
        assertThatThrownBy(() -> service.register("05551234567", "Ayşe", "auth-secret"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).existsByPhone(any());
    }
}
