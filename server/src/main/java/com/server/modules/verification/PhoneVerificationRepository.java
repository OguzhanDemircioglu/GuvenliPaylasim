package com.server.modules.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findByPhoneAndPurpose(String phone, VerificationPurpose purpose);

    @Modifying
    @Query("delete from PhoneVerification v where v.phone = :phone and v.purpose = :purpose")
    void deleteByPhoneAndPurpose(String phone, VerificationPurpose purpose);

    @Transactional
    @Modifying
    @Query("delete from PhoneVerification v where v.expiresAt < :now")
    int deleteExpired(Instant now);
}
