package com.server.modules.user;

import com.server.common.exception.BusinessException;
import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.modules.auth.AuthMessages;
import com.server.modules.auth.LoginAttemptService;
import com.server.modules.totp.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * "Hesabımı sil" (Crypto_Design §7.13): kişi ve kişiyle ilgili her şey kalıcı olarak silinir, "silindi" işareti
 * tutulmaz (gizlilik kararı §8, KVKK m.7). Silinenler: hesap, anahtarlar, kurtarma kodları, oturumlar, kişilikler (iki
 * yönde), gönderdiği ve numarasına gelmiş davetler, gelen kutuları (son alıcıysa belgenin kendisi), gönderdiği kutular
 * (bütün alıcılarından), bekleyen doğrulama kodları. Kalanlar: kötüye kullanım sınırları için giriş denemesi ve kod
 * isteği kayıtları (24 saatte kendiliğinden silinir).
 * <p>
 * Tablolar arasında yabancı anahtar yok; silme sırası burada, tek transaction'da.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final LoginAttemptService loginAttemptService;
    private final JdbcTemplate jdbc;

    /**
     * {@code password}: istemcinin paroladan türettiği authSecret. İki adımlı doğrulama açıksa {@code totpCode}
     * (Authenticator ya da kurtarma kodu) zorunlu. Yanlış denemeler sayılır; sınır aşılınca bir saat kilitlenir.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void delete(Long userId, String password, String totpCode) {
        String attemptKey = "delete_" + userId;
        loginAttemptService.countAttempt(attemptKey);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(GlobalMessages.RECORD_NOT_FOUND));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(AuthMessages.CURRENT_PASSWORD_INCORRECT);
        }
        if (user.isTotpEnabled()) {
            if (totpCode == null || totpCode.isBlank()) {
                throw new BusinessException(AuthMessages.TOTP_REQUIRED);
            }
            if (!totpService.verifyWithin(user, totpCode.trim())) {
                throw new BusinessException(AuthMessages.TOTP_INVALID);
            }
        }
        loginAttemptService.clear(attemptKey);
        // Tüketilen TOTP kodu gibi bekleyen değişiklik önce yazılır; yoksa commit'te silinmiş satıra UPDATE gider.
        userRepository.flush();
        erase(user.getId(), user.getPhone());
        log.info("Account {} deleted with all related data", userId);
    }

    void erase(Long userId, String phone) {
        // Gelen kutuları: önce kişinin alıcı satırları, sonra alıcısı kalmayan belgeler.
        List<Long> received = jdbc.queryForList(
                "select document_id from document_recipients where recipient_id = ?", Long.class, userId);
        jdbc.update("delete from document_recipients where recipient_id = ?", userId);
        for (Long documentId : received) {
            jdbc.update("""
                    delete from documents d where d.id = ?
                      and not exists (select 1 from document_recipients r where r.document_id = d.id)""", documentId);
        }
        // Gönderdiği kutular, bütün alıcılarından.
        jdbc.update("""
                delete from document_recipients
                 where document_id in (select id from documents where sender_id = ?)""", userId);
        jdbc.update("delete from documents where sender_id = ?", userId);

        jdbc.update("delete from contacts where owner_id = ? or contact_id = ?", userId, userId);
        jdbc.update("delete from invites where sender_id = ? or recipient_phone = ?", userId, phone);
        jdbc.update("delete from totp_recovery_codes where user_id = ?", userId);
        jdbc.update("delete from user_sessions where user_id = ?", userId);
        jdbc.update("delete from user_keys where user_id = ?", userId);
        jdbc.update("delete from phone_verifications where phone = ?", phone);
        jdbc.update("delete from users where id = ?", userId);
    }
}
