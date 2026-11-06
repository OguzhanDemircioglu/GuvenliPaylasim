package com.server.modules.document;

import com.server.common.exception.BusinessException;
import com.server.modules.invite.InviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Gönderim kotaları (Crypto_Design §7.9): kullanıcı başına günde 200 kutu; aynı anda yaşayan gönderilmiş kutuların
 * (kabul edilmemiş davetler dahil) toplamı en fazla 500 MB. Kutular en fazla 30 gün yaşadığı için depolama kendini
 * temizler; kota sadece kötüye kullanımı durdurur.
 */
@Component
@RequiredArgsConstructor
public class SendQuota {

    static final int DAILY_LIMIT = 200;
    static final long STORAGE_LIMIT_BYTES = 500L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final InviteRepository inviteRepository;

    /** Sınır aşılırsa 422; yeni kutunun boyutu da hesaba katılır. */
    public void check(Long senderId, long newBytes) {
        Instant now = Instant.now();
        if (documentRepository.countBySenderIdAndCreatedAtAfter(senderId, now.minus(Duration.ofDays(1))) >= DAILY_LIMIT) {
            throw new BusinessException(DocumentMessages.DAILY_LIMIT);
        }
        long live = documentRepository.sumLiveSize(senderId, now) + inviteRepository.sumPendingSize(senderId, now);
        if (live + newBytes > STORAGE_LIMIT_BYTES) {
            throw new BusinessException(DocumentMessages.QUOTA_EXCEEDED);
        }
    }
}
