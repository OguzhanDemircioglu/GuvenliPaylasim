package com.server.modules.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {

    /**
     * Transaction sonuna kadar süren PostgreSQL kilidi: aynı anahtar (numara ya da IP) için kod istekleri sırayla
     * işlenir, böylece eş zamanlı istekler sayım sınırını aşamaz. Dönen değer anlamsızdır (her zaman 1).
     */
    @Query(nativeQuery = true, value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS l")
    int lock(String key);

    long countByPhoneAndCreatedAtAfter(String phone, Instant after);

    long countByIpAndCreatedAtAfter(String ip, Instant after);

    long countByCreatedAtAfter(Instant after);

    @Transactional
    @Modifying
    @Query("delete from VerificationRequest r where r.createdAt < :before")
    int deleteOlderThan(Instant before);
}
