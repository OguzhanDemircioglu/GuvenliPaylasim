package com.server.modules.auth;

import com.server.common.exception.BusinessException;
import com.server.common.exception.LoginBlockedException;
import com.server.common.exception.UnauthorizedException;
import com.server.modules.auth.dto.ChangePasswordRequest;
import com.server.modules.auth.dto.LoginRequest;
import com.server.modules.key.KeyService;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.menu.MenuService;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import com.server.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login, refresh token rotation (30 saniyelik pencere, çalınma tespiti), logout ve parola değiştirme.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "ayse.yilmaz@gps.local";
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "test-agent");
    private static final String ATTEMPT_KEY = "10.0.0.1_" + EMAIL;
    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private MenuService menuService;
    @Mock
    private AccessTokenIssuer accessTokenIssuer;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private KeyService keyService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, sessionRepository, loginAttemptService, menuService,
                accessTokenIssuer, passwordEncoder,
                new SessionProperties(Duration.ofDays(90), Duration.ofSeconds(30), false), keyService);
    }

    @Test
    void loginOpensSessionStoresOnlyTokenHashAndClearsFailures() {
        User user = user(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        stubSessionInfo(user);

        AuthResult result = service.login(new LoginRequest(EMAIL, "secret"), CLIENT);

        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.sessionInfo().accessToken()).isEqualTo("access-token");
        ArgumentCaptor<UserSession> saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getCurrentTokenHash())
                .isEqualTo(RefreshTokens.hash(result.refreshToken()))
                .isNotEqualTo(result.refreshToken());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getIp()).isEqualTo(CLIENT.ip());
        verify(loginAttemptService).clear(ATTEMPT_KEY);
    }

    @Test
    void wrongPasswordIsCountedAndRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "wrong"), CLIENT))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthMessages.INVALID_CREDENTIALS);
        verify(loginAttemptService).recordFailure(ATTEMPT_KEY);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void unknownEmailIsCountedAndGetsTheSameAnswer() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "secret"), CLIENT))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthMessages.INVALID_CREDENTIALS);
        // Kayıtlı olmayan e-postada da şifre kontrolü yapılır (zamanlama farkı olmasın).
        verify(passwordEncoder).matches(eq("secret"), any());
        verify(loginAttemptService).recordFailure(ATTEMPT_KEY);
    }

    @Test
    void blockedLoginIsRejectedEvenWithCorrectPassword() {
        doThrow(new LoginBlockedException(AuthMessages.LOGIN_BLOCKED)).when(loginAttemptService).ensureNotBlocked(ATTEMPT_KEY);

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "secret"), CLIENT))
                .isInstanceOf(LoginBlockedException.class)
                .hasMessage(AuthMessages.LOGIN_BLOCKED);
        verify(userRepository, never()).findByEmail(anyString());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void inactiveUserCannotLogin() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "secret"), CLIENT))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthMessages.USER_INACTIVE);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void refreshRotatesTokenAndReturnsNewOne() {
        String token = RefreshTokens.generate();
        String hash = RefreshTokens.hash(token);
        User user = user(true);
        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(hash, hash))
                .thenReturn(Optional.of(session(hash, null, null, null)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(sessionRepository.rotate(eq(SESSION_ID), eq(hash), anyString(), any(), any())).thenReturn(1);
        stubSessionInfo(user);

        AuthResult result = service.refresh(token);

        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo(token);
        verify(sessionRepository).rotate(eq(SESSION_ID), eq(hash), eq(RefreshTokens.hash(result.refreshToken())), any(), any());
        verify(sessionRepository, never()).revoke(any(), any());
    }

    @Test
    void previousTokenWithinGracePeriodGetsAccessTokenButKeepsCookie() {
        String oldToken = RefreshTokens.generate();
        String oldHash = RefreshTokens.hash(oldToken);
        User user = user(true);
        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(oldHash, oldHash)).thenReturn(Optional.of(
                session("current-hash", oldHash, Instant.now().minusSeconds(5), null)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        stubSessionInfo(user);

        AuthResult result = service.refresh(oldToken);

        assertThat(result.refreshToken()).isNull();
        assertThat(result.sessionInfo().accessToken()).isEqualTo("access-token");
        verify(sessionRepository, never()).revoke(any(), any());
    }

    @Test
    void previousTokenAfterGracePeriodIsTheftAndRevokesSession() {
        String oldToken = RefreshTokens.generate();
        String oldHash = RefreshTokens.hash(oldToken);
        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(oldHash, oldHash)).thenReturn(Optional.of(
                session("current-hash", oldHash, Instant.now().minusSeconds(60), null)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true)));

        assertThatThrownBy(() -> service.refresh(oldToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthMessages.SESSION_EXPIRED);
        verify(sessionRepository).revoke(eq(SESSION_ID), any());
    }

    @Test
    void concurrentRotationFallsBackToGracePeriod() {
        String token = RefreshTokens.generate();
        String hash = RefreshTokens.hash(token);
        User user = user(true);
        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(hash, hash))
                .thenReturn(Optional.of(session(hash, null, null, null)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        // Başka bir sekme aynı anda yeniledi: rotate 0 döner, güncel kayıtta bu token artık "önceki" token.
        when(sessionRepository.rotate(eq(SESSION_ID), eq(hash), anyString(), any(), any())).thenReturn(0);
        when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session("other-tab-hash", hash, Instant.now(), null)));
        stubSessionInfo(user);

        AuthResult result = service.refresh(token);

        assertThat(result.refreshToken()).isNull();
        verify(sessionRepository, never()).revoke(any(), any());
    }

    @Test
    void revokedSessionCannotRefresh() {
        String token = RefreshTokens.generate();
        String hash = RefreshTokens.hash(token);
        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(hash, hash))
                .thenReturn(Optional.of(session(hash, null, null, Instant.now().minusSeconds(1))));

        assertThatThrownBy(() -> service.refresh(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthMessages.SESSION_EXPIRED);
        verify(sessionRepository, never()).rotate(any(), any(), any(), any(), any());
    }

    @Test
    void unknownOrMissingTokenCannotRefresh() {
        assertThatThrownBy(() -> service.refresh(null)).hasMessage(AuthMessages.SESSION_EXPIRED);
        assertThatThrownBy(() -> service.refresh("")).hasMessage(AuthMessages.SESSION_EXPIRED);

        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(anyString(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh("unknown")).hasMessage(AuthMessages.SESSION_EXPIRED);
    }

    @Test
    void deactivatedUserLosesSessionOnRefresh() {
        String token = RefreshTokens.generate();
        String hash = RefreshTokens.hash(token);
        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(hash, hash))
                .thenReturn(Optional.of(session(hash, null, null, null)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> service.refresh(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AuthMessages.USER_INACTIVE);
        verify(sessionRepository).revoke(eq(SESSION_ID), any());
    }

    @Test
    void logoutRevokesSession() {
        String token = RefreshTokens.generate();
        String hash = RefreshTokens.hash(token);
        when(sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(hash, hash))
                .thenReturn(Optional.of(session(hash, null, null, null)));

        service.logout(token);

        verify(sessionRepository).revoke(eq(SESSION_ID), any());
    }

    @Test
    void changePasswordStoresNewKeysAndClosesOtherSessions() {
        User user = user(true);
        user.setMustChangePassword(true);
        KeyBundleRequest keys = new KeyBundleRequest("enc", "sig", "bundle");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-secret", "hash")).thenReturn(true);
        when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

        service.changePassword(USER_ID, SESSION_ID, new ChangePasswordRequest("old-secret", "new-secret", keys));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.isMustChangePassword()).isFalse();
        verify(keyService).saveOnPasswordChange(USER_ID, keys);
        verify(sessionRepository).revokeAllByUserIdExcept(eq(USER_ID), eq(SESSION_ID), any());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(USER_ID, SESSION_ID,
                new ChangePasswordRequest("wrong", "new-secret", new KeyBundleRequest("enc", "sig", "bundle"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthMessages.CURRENT_PASSWORD_INCORRECT);
        verify(keyService, never()).saveOnPasswordChange(any(), any());
        verify(sessionRepository, never()).revokeAllByUserIdExcept(any(), any(), any());
    }

    private void stubSessionInfo(User user) {
        when(accessTokenIssuer.issue(eq(user), any())).thenReturn("access-token");
        when(accessTokenIssuer.expiresInSeconds()).thenReturn(900L);
        when(menuService.getMenuTree(user.getRole())).thenReturn(List.of());
    }

    private static User user(boolean active) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setFullName("Ayşe Yılmaz");
        user.setPassword("hash");
        user.setRole(Role.USER);
        user.setActive(active);
        return user;
    }

    private static UserSession session(String currentHash, String previousHash, Instant rotatedAt, Instant revokedAt) {
        Instant now = Instant.now();
        UserSession session = withId(UserSession.open(USER_ID, currentHash, CLIENT, now.minusSeconds(3600),
                now.plus(Duration.ofDays(30))));
        ReflectionTestUtils.setField(session, "previousTokenHash", previousHash);
        ReflectionTestUtils.setField(session, "rotatedAt", rotatedAt);
        ReflectionTestUtils.setField(session, "revokedAt", revokedAt);
        return session;
    }

    private static UserSession withId(UserSession session) {
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }
}
