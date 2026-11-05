package com.server.modules.user;

import com.server.common.entity.BaseEntity;
import com.server.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SoftDelete;

@Getter
@Setter
@Entity
@SoftDelete
@Table(name = "users")
public class User extends BaseEntity {

    // Giriş e-posta ile yapılır. Benzersizlik silinmemiş kayıtlara özel index ile sağlanır (V001_UniqueIndexes).
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Kullanıcının bağlı olduğu birim (units.id). Belgeler birime göre seçilen kişilere gönderilir. */
    @Column(name = "unit_id")
    private Long unitId;

    /** SMS bildirimi için; SMS servisi bağlanana kadar sadece simülasyonda gösterilir. */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Yöneticinin verdiği ilk parola sistem kullanılmadan önce değiştirilmelidir.
     * DB varsayılanı, kolonu yazmayan eski migration'lar (V002) için (Backend_Architecture §11 kural 5).
     */
    @ColumnDefault("false")
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;
}
