package com.server.modules.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {

    long countByPhoneAndCreatedAtAfter(String phone, Instant after);

    long countByIpAndCreatedAtAfter(String ip, Instant after);

    long countByCreatedAtAfter(Instant after);

    @Transactional
    @Modifying
    @Query("delete from VerificationRequest r where r.createdAt < :before")
    int deleteOlderThan(Instant before);
}
