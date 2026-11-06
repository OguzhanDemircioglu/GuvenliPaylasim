package com.server.modules.totp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tek kullanımlık kurtarma kodu (Authenticator kaybolursa). Kodun kendisi tutulmaz, sadece
 * {@code SHA-256("gps-recovery/v1|" + kullanıcı + "|" + kod)}. Kullanılınca satır silinir. Teknik tablo.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "totp_recovery_codes", indexes = @Index(name = "ix_totp_recovery_user", columnList = "user_id"))
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    RecoveryCode(Long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }
}
