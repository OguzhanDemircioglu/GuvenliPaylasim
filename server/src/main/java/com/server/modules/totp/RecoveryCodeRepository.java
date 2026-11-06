package com.server.modules.totp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByUserIdAndCodeHash(Long userId, String codeHash);

    @Modifying
    @Query("delete from RecoveryCode r where r.userId = :userId")
    void deleteByUserId(Long userId);
}
