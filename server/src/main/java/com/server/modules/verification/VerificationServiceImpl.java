package com.server.modules.verification;

import com.server.common.exception.BusinessException;
import com.server.common.exception.LoginBlockedException;
import com.server.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final PhoneVerificationRepository verificationRepository;
    private final VerificationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final VerificationMessageSender sender;
    private final VerificationProperties properties;

    @Override
    @Transactional
    public java.util.Optional<String> start(String phone, VerificationPurpose purpose, String ip) {
        Instant now = Instant.now();
        enforceLimits(phone, ip, now);
        requestRepository.save(new VerificationRequest(phone, ip, now));

        String code = VerificationCodes.generate();
        verificationRepository.deleteByPhoneAndPurpose(phone, purpose);
        verificationRepository.flush();
        PhoneVerification verification = new PhoneVerification();
        verification.setPhone(phone);
        verification.setPurpose(purpose);
        verification.setCodeHash(VerificationCodes.hash(phone, purpose, code));
        verification.setExpiresAt(now.plus(properties.codeTtl()));
        verification.setCreatedAt(now);
        verificationRepository.save(verification);

        // SADECE TEST: kayıt ve parola sıfırlama kodu gönderilmez, istemciye döner (her numara için aynı davranış).
        // Açıkken numarayı bilen herkes o hesabın parolasını sıfırlayabilir (TOTP açık hesap hariç); gerçek
        // kullanıcıdan önce kapatılır.
        if (properties.testMode()) {
            log.warn("Verification TEST MODE: {} code returned to the client, no message sent", purpose);
            return java.util.Optional.of(code);
        }

        // Buraya kadar her numara için aynı işler yapıldı. Mesaj sadece uygunsa ve istek thread'inden ayrı gider.
        boolean registered = userRepository.existsByPhone(phone);
        boolean eligible = purpose == VerificationPurpose.REGISTER ? !registered : registered;
        if (eligible) {
            sender.enqueue(phone, message(purpose, code));
        }
        return java.util.Optional.empty();
    }

    @jakarta.annotation.PostConstruct
    void warnIfTestMode() {
        if (properties.testMode()) {
            log.warn("TEST_MODE is ON: phone ownership is NOT verified at registration or password reset. "
                    + "Turn it off before real users.");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public void verify(String phone, VerificationPurpose purpose, String code) {
        PhoneVerification verification = verificationRepository.findByPhoneAndPurpose(phone, purpose)
                .orElseThrow(VerificationServiceImpl::invalid);
        if (!verification.getExpiresAt().isAfter(Instant.now())) {
            verificationRepository.delete(verification);
            throw invalid();
        }
        if (!VerificationCodes.matches(verification.getCodeHash(), phone, purpose, code)) {
            verification.setAttempts(verification.getAttempts() + 1);
            if (verification.getAttempts() >= properties.maxAttempts()) {
                verificationRepository.delete(verification);
            }
            throw invalid();
        }
        verificationRepository.delete(verification);
    }

    /**
     * Sınır cevapları numaranın kayıtlı olup olmadığına bağlı değildir. Aynı numara ve aynı IP için istekler kilitle
     * sırayla işlenir (önce numara, sonra IP; sıra hep aynı olduğu için kilitlenme olmaz).
     */
    private void enforceLimits(String phone, String ip, Instant now) {
        requestRepository.lock("verification_phone:" + phone);
        requestRepository.lock("verification_ip:" + ip);
        if (requestRepository.countByCreatedAtAfter(now.minus(DAY)) >= properties.dailyLimit()) {
            log.error("Verification daily limit ({}) reached; no more codes are sent today", properties.dailyLimit());
            throw rateLimited();
        }
        if (requestRepository.countByPhoneAndCreatedAtAfter(phone, now.minus(HOUR)) >= properties.perPhoneHourly()
                || requestRepository.countByIpAndCreatedAtAfter(ip, now.minus(HOUR)) >= properties.perIpHourly()) {
            throw rateLimited();
        }
    }

    private String message(VerificationPurpose purpose, String code) {
        String action = purpose == VerificationPurpose.REGISTER ? "kayıt" : "parola sıfırlama";
        return "Lockpost " + action + " kodunuz: " + code + ". " + properties.codeTtl().toMinutes()
                + " dakika geçerlidir. Bu kodu kimseyle paylaşmayın.";
    }

    private static BusinessException invalid() {
        return new BusinessException(VerificationMessages.CODE_INVALID);
    }

    private static LoginBlockedException rateLimited() {
        return new LoginBlockedException(VerificationMessages.RATE_LIMITED);
    }
}
