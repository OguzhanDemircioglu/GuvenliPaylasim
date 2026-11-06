package com.server.modules.invite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    /** Sadece bu telefona bırakılmış, süresi dolmamış, kabul edilmemiş davetler arasında arar (Crypto_Design §7.6). */
    @Query("""
            select i from Invite i
             where i.recipientPhone = :phone and i.verifierHash = :verifierHash
               and i.acceptedAt is null and i.expiresAt > :now""")
    Optional<Invite> findRedeemable(String phone, String verifierHash, Instant now);

    long countBySenderIdAndCreatedAtAfter(Long senderId, Instant after);

    /** Gönderenin kabul mührünü doğrulaması gereken davetler. */
    List<Invite> findBySenderIdAndAcceptedAtIsNotNullAndCompletedAtIsNull(Long senderId);

    /** Gönderenin henüz kabul edilmemiş davetleri. */
    List<Invite> findBySenderIdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Long senderId, Instant now);

    /**
     * Süresi dolup kabul edilmemiş davetler içerikleriyle kalıcı olarak silinir. Tamamlananlar (sadece meta veri kalır)
     * 1 gün sonra, kabul edilip gönderenin hiç doğrulamadığı davetler 7 gün sonra silinir.
     */
    @Transactional
    @Modifying
    @Query("""
            delete from Invite i
             where (i.acceptedAt is null and i.expiresAt < :now)
                or (i.completedAt is not null and i.completedAt < :completedBefore)
                or (i.acceptedAt is not null and i.completedAt is null and i.acceptedAt < :acceptedBefore)""")
    int deleteFinished(Instant now, Instant completedBefore, Instant acceptedBefore);
}
