package com.server.modules.totp;

import com.server.common.exception.BusinessException;
import com.server.common.exception.LoginBlockedException;
import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.modules.auth.AuthMessages;
import com.server.modules.auth.LoginAttemptRepository;
import com.server.modules.auth.LoginProtectionProperties;
import com.server.modules.totp.dto.TotpEnabledResponse;
import com.server.modules.totp.dto.TotpSetupResponse;
import com.server.modules.user.User;
import com.server.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TotpServiceImpl implements TotpService {

    static final String ISSUER = "Lockpost";
    static final int RECOVERY_CODE_COUNT = 10;
    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptRepository attemptRepository;
    private final LoginProtectionProperties protection;

    @Override
    @Transactional
    public TotpSetupResponse setup(Long userId) {
        User user = findUser(userId);
        if (user.isTotpEnabled()) {
            throw new BusinessException(TotpMessages.ALREADY_ENABLED);
        }
        String secret = Totp.base32(Totp.newSecret());
        user.setTotpPendingSecret(secret);
        return new TotpSetupResponse(secret, Totp.uri(ISSUER, user.getPhone(), secret));
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public TotpEnabledResponse enable(Long userId, String code) {
        String attemptKey = attemptKey(userId);
        ensureNotBlocked(attemptKey);
        User user = findUser(userId);
        if (user.isTotpEnabled()) {
            throw new BusinessException(TotpMessages.ALREADY_ENABLED);
        }
        if (user.getTotpPendingSecret() == null) {
            throw new BusinessException(TotpMessages.SETUP_REQUIRED);
        }
        long step = Totp.matchingStep(Totp.fromBase32(user.getTotpPendingSecret()), digits(code), Totp.currentStep(), -1);
        if (step < 0) {
            recordFailure(attemptKey);
            throw new BusinessException(TotpMessages.CODE_INVALID);
        }
        user.setTotpSecret(user.getTotpPendingSecret());
        user.setTotpPendingSecret(null);
        user.setTotpEnabled(true);
        user.setTotpLastStep(step);
        attemptRepository.deleteById(attemptKey);
        return new TotpEnabledResponse(newRecoveryCodes(userId));
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public void disable(Long userId, String password, String code) {
        String attemptKey = attemptKey(userId);
        ensureNotBlocked(attemptKey);
        User user = findUser(userId);
        if (!user.isTotpEnabled()) {
            throw new BusinessException(TotpMessages.NOT_ENABLED);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordFailure(attemptKey);
            throw new BusinessException(AuthMessages.CURRENT_PASSWORD_INCORRECT);
        }
        if (!consume(user, code)) {
            recordFailure(attemptKey);
            throw new BusinessException(TotpMessages.CODE_INVALID);
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpPendingSecret(null);
        user.setTotpLastStep(null);
        recoveryCodeRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(Long userId, String code) {
        User user = findUser(userId);
        return user.isTotpEnabled() && consume(user, code);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verifyWithin(User user, String code) {
        return user.isTotpEnabled() && consume(user, code);
    }

    /** Authenticator kodu ya da kurtarma kodu; başarılıysa tekrar kullanılamaz hale getirilir. */
    private boolean consume(User user, String code) {
        if (code == null) {
            return false;
        }
        String digits = digits(code);
        if (digits.matches("\\d{6}")) {
            long after = user.getTotpLastStep() == null ? -1 : user.getTotpLastStep();
            long step = Totp.matchingStep(Totp.fromBase32(user.getTotpSecret()), digits, Totp.currentStep(), after);
            if (step < 0) {
                return false;
            }
            user.setTotpLastStep(step);
            return true;
        }
        return recoveryCodeRepository.findByUserIdAndCodeHash(user.getId(), recoveryHash(user.getId(), code))
                .map(found -> {
                    recoveryCodeRepository.delete(found);
                    return true;
                })
                .orElse(false);
    }

    private List<String> newRecoveryCodes(Long userId) {
        recoveryCodeRepository.deleteByUserId(userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                code.append(CROCKFORD.charAt(RANDOM.nextInt(32)));
            }
            String formatted = code.substring(0, 5) + "-" + code.substring(5);
            recoveryCodeRepository.save(new RecoveryCode(userId, recoveryHash(userId, formatted)));
            codes.add(formatted);
        }
        return codes;
    }

    /** Boşluk ve tire atılır, büyük harfe çevrilir; O→0, I/L→1 (Crockford). */
    static String recoveryHash(Long userId, String code) {
        String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "")
                .replace('O', '0').replace('I', '1').replace('L', '1');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("gps-recovery/v1|" + userId + "|" + normalized).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String digits(String code) {
        return code == null ? "" : code.replaceAll("\\s", "");
    }

    private void ensureNotBlocked(String key) {
        Instant now = Instant.now();
        if (attemptRepository.findById(key).filter(a -> a.isBlocked(now)).isPresent()) {
            throw new LoginBlockedException(TotpMessages.BLOCKED);
        }
    }

    private void recordFailure(String key) {
        Instant now = Instant.now();
        attemptRepository.recordFailure(key, now, now.plus(protection.blockDuration()), protection.maxAttempts());
    }

    private static String attemptKey(Long userId) {
        return "totp_" + userId;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException(GlobalMessages.RECORD_NOT_FOUND));
    }
}
