package com.server.modules.user;

import com.server.common.entity.BaseEntity;
import com.server.security.SealedColumns;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * v2: rol, birim ve e-posta yoktur; herkes eşit kullanıcıdır (docs/Crypto_Design.md §7.1). Giriş telefonla yapılır.
 * Telefon parola tuzunda kullanıldığı için değiştirilemez (§7.3).
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    /** E.164: {@code +905XXXXXXXXX} (Phones). */
    @Column(name = "phone", nullable = false, unique = true, length = 13, updatable = false)
    private String phone;

    /**
     * İstemcinin paroladan türettiği authSecret'ın bcrypt özeti; gerçek parola sunucuya gelmez. Veritabanında ayrıca
     * sunucu anahtarıyla kilitlidir (ServerSeal, Security.md S2).
     */
    @Convert(converter = SealedColumns.PasswordHash.class)
    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;
}
