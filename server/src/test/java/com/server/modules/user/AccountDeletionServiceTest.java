package com.server.modules.user;

import com.server.common.exception.BusinessException;
import com.server.modules.auth.AuthMessages;
import com.server.modules.auth.LoginAttemptService;
import com.server.modules.totp.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hesap silme: parola (ve açıksa TOTP) doğrulanmadan hiçbir şey silinmez; doğrulanınca her şey, hesap en son. */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    private static final String PHONE = "+905551234567";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TotpService totpService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private JdbcTemplate jdbc;

    private AccountDeletionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(userRepository, passwordEncoder, totpService, loginAttemptService, jdbc);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setPhone(PHONE);
        user.setPassword("hash");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void wrongPasswordDeletesNothingAndCountsTheAttempt() {
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L, "wrong", null))
                .isInstanceOf(BusinessException.class).hasMessage(AuthMessages.CURRENT_PASSWORD_INCORRECT);
        verify(loginAttemptService).countAttempt("delete_7");
        verify(loginAttemptService, never()).clear("delete_7");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void totpIsRequiredWhenEnabled() {
        user.setTotpEnabled(true);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L, "secret", " "))
                .isInstanceOf(BusinessException.class).hasMessage(AuthMessages.TOTP_REQUIRED);
        when(totpService.verifyWithin(user, "123456")).thenReturn(false);
        assertThatThrownBy(() -> service.delete(7L, "secret", "123456"))
                .isInstanceOf(BusinessException.class).hasMessage(AuthMessages.TOTP_INVALID);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void verifiedDeletionErasesEverythingAndTheAccountLast() {
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(11L));

        service.delete(7L, "secret", null);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).update(contains("delete from document_recipients where recipient_id"), eq(7L));
        order.verify(jdbc).update(contains("delete from documents d where d.id"), eq(11L));
        order.verify(jdbc).update(contains("where document_id in (select id from documents where sender_id"), eq(7L));
        order.verify(jdbc).update(contains("delete from documents where sender_id"), eq(7L));
        order.verify(jdbc).update(contains("delete from contacts"), eq(7L), eq(7L));
        order.verify(jdbc).update(contains("delete from invites"), eq(7L), eq(PHONE));
        order.verify(jdbc).update(contains("delete from totp_recovery_codes"), eq(7L));
        order.verify(jdbc).update(contains("delete from user_sessions"), eq(7L));
        order.verify(jdbc).update(contains("delete from user_keys"), eq(7L));
        order.verify(jdbc).update(contains("delete from phone_verifications"), eq(PHONE));
        order.verify(jdbc).update(contains("delete from users"), eq(7L));
        verify(loginAttemptService).clear("delete_7");
    }
}
