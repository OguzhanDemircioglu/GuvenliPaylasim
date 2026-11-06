package com.server.modules.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Bekleyen doğrulama kodu (Crypto_Design §7.2). Telefon ve amaç başına en fazla bir kayıt vardır; yeni kod eskisinin
 * yerine geçer. Kodun kendisi tutulmaz, sadece özeti (VerificationCodes.hash). Teknik tablo: BaseEntity'den türemez.
 */
@Getter
@Setter
@Entity
@Table(name = "phone_verifications",
        uniqueConstraints = @UniqueConstraint(name = "ux_phone_verifications", columnNames = {"phone", "purpose"}))
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** E.164. */
    @Column(name = "phone", nullable = false, length = 13)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
