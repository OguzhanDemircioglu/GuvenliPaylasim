package com.server.modules.auth;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.common.exception.UnauthorizedException;
import com.server.common.message.GlobalMessages;
import com.server.modules.auth.dto.ChangePasswordRequest;
import com.server.modules.auth.dto.LoginRequest;
import com.server.modules.auth.dto.SessionInfo;
import com.server.modules.auth.dto.SessionUser;
import com.server.modules.menu.MenuService;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Login, refresh ve logout bilinçli olarak tek bir transaction içinde çalışmaz: yanlış deneme kaydı ve
 * çalınma tespitindeki oturum iptali, hemen ardından fırlatılan exception ile geri alınmamalıdır.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginAttemptService loginAttemptService;
    private final MenuService menuService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final SessionProperties properties;
    /** Kayıtlı olmayan e-postalarda da şifre kontrolü yapılır; cevap süresinden kullanıcının varlığı anlaşılmasın. */
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository userRepository, UserSessionRepository sessionRepository,
                           LoginAttemptService loginAttemptService, MenuService menuService,
                           AccessTokenIssuer accessTokenIssuer, PasswordEncoder passwordEncoder,
                           SessionProperties properties) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.loginAttemptService = loginAttemptService;
        this.menuService = menuService;
        this.accessTokenIssuer = accessTokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public AuthResult login(LoginRequest request, ClientInfo client) {
        String attemptKey = client.ip() + "_" + request.email();
        loginAttemptService.ensureNotBlocked(attemptKey);

        Optional<User> user = userRepository.findByEmail(request.email());
        String passwordHash = user.map(User::getPassword).orElse(dummyPasswordHash);
        if (!passwordEncoder.matches(request.password(), passwordHash) || user.isEmpty()) {
            loginAttemptService.recordFailure(attemptKey);
            throw new UnauthorizedException(AuthMessages.INVALID_CREDENTIALS);
        }
        if (!user.get().isActive()) {
            throw new UnauthorizedException(AuthMessages.USER_INACTIVE);
        }
        loginAttemptService.clear(attemptKey);
        return openSession(user.get(), client);
    }

    @Override
    public AuthResult devLogin(String email, ClientInfo client) {
        User user = userRepository.findByEmail(email).filter(User::isActive)
                .orElseThrow(() -> new NotFoundException(GlobalMessages.RECORD_NOT_FOUND));
        return openSession(user, client);
    }

    private AuthResult openSession(User user, ClientInfo client) {
        Instant now = Instant.now();
        String refreshToken = RefreshTokens.generate();
        UserSession session = sessionRepository.save(UserSession.open(user.getId(),
                RefreshTokens.hash(refreshToken), client, now, now.plus(properties.refreshTokenTtl())));
        return new AuthResult(buildSessionInfo(user, session.getId()), refreshToken);
    }

    @Override
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw sessionExpired();
        }
        String hash = RefreshTokens.hash(refreshToken);
        UserSession session = findSession(hash).orElseThrow(AuthServiceImpl::sessionExpired);
        Instant now = Instant.now();
        if (!session.isUsable(now)) {
            throw sessionExpired();
        }

        User user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            sessionRepository.revoke(session.getId(), now);
            throw new UnauthorizedException(user == null ? AuthMessages.SESSION_EXPIRED : AuthMessages.USER_INACTIVE);
        }

        if (hash.equals(session.getCurrentTokenHash())) {
            String newToken = RefreshTokens.generate();
            int rotated = sessionRepository.rotate(session.getId(), hash, RefreshTokens.hash(newToken),
                    now, now.plus(properties.refreshTokenTtl()));
            if (rotated == 1) {
                return new AuthResult(buildSessionInfo(user, session.getId()), newToken);
            }
            // Aynı anda başka bir sekme yeniledi: güncel kaydı okuyup 30 saniyelik pencereye göre karar ver.
            session = sessionRepository.findById(session.getId()).orElseThrow(AuthServiceImpl::sessionExpired);
        }
        return reuseWithinGracePeriod(session, hash, user, now);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        findSession(RefreshTokens.hash(refreshToken))
                .ifPresent(session -> sessionRepository.revoke(session.getId(), Instant.now()));
    }

    @Override
    @Transactional
    public void changePassword(Long userId, Long currentSessionId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(GlobalMessages.RECORD_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(AuthMessages.CURRENT_PASSWORD_INCORRECT);
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        sessionRepository.revokeAllByUserIdExcept(userId, currentSessionId, Instant.now());
    }

    /**
     * Gelen token bir önceki token. Son yenilemenin üzerinden 30 saniye geçmediyse birden fazla sekme aynı anda
     * yenileme yapmıştır: sadece yeni access token verilir, cookie'ye dokunulmaz. Süre geçtiyse token çalınmış sayılır.
     */
    private AuthResult reuseWithinGracePeriod(UserSession session, String hash, User user, Instant now) {
        boolean withinGracePeriod = session.isUsable(now)
                && hash.equals(session.getPreviousTokenHash())
                && session.getRotatedAt() != null
                && !now.isAfter(session.getRotatedAt().plus(properties.reuseGracePeriod()));
        if (!withinGracePeriod) {
            sessionRepository.revoke(session.getId(), now);
            log.warn("Refresh token reuse detected; session {} of user {} revoked", session.getId(), user.getId());
            throw sessionExpired();
        }
        return new AuthResult(buildSessionInfo(user, session.getId()), null);
    }

    /** Login ve refresh'in ortak cevabı tek yerde üretilir. */
    private SessionInfo buildSessionInfo(User user, Long sessionId) {
        return new SessionInfo(
                accessTokenIssuer.issue(user, sessionId),
                accessTokenIssuer.expiresInSeconds(),
                SessionUser.from(user),
                menuService.getMenuTree(user.getRole()));
    }

    private Optional<UserSession> findSession(String tokenHash) {
        return sessionRepository.findByCurrentTokenHashOrPreviousTokenHash(tokenHash, tokenHash);
    }

    private static UnauthorizedException sessionExpired() {
        return new UnauthorizedException(AuthMessages.SESSION_EXPIRED);
    }
}
