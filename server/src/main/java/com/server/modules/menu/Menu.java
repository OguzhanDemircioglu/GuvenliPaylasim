package com.server.modules.menu;

import com.server.common.entity.BaseEntity;
import com.server.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

import java.util.EnumSet;
import java.util.Set;

/**
 * Menüler sadece migration class'larıyla yönetilir. Ağaç parentId üzerinden Java'da kurulur;
 * bilinçli olarak JPA self-ilişkisi (@ManyToOne parent / @OneToMany children) kurulmaz.
 */
@Getter
@Setter
@Entity
@SoftDelete
@Table(name = "menus")
public class Menu extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId;

    // Benzersizlik silinmemiş kayıtlara özel index ile sağlanır (V001_UniqueIndexes).
    @Column(name = "title_key", nullable = false, length = 100)
    private String titleKey;

    @Column(name = "route", length = 200)
    private String route;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Menüyü görebilen en düşük roller, virgülle ayrılmış (ör. "USER,OWNER"). Hiyerarşi uygulanır: "USER" yazan
     * menüyü ADMIN ve SUPERADMIN de görür (MenuServiceImpl). Boşsa sadece SUPERADMIN görür.
     */
    @Convert(converter = RoleSetConverter.class)
    @Column(name = "roles", length = 100)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    /**
     * Kullanılmaz; yerine {@link #roles}. Kolon sadece V003–V005'in boş bir veritabanında çalışabilmesi için durur
     * (o migration'lar bu kolona yazar ve değiştirilemez). V006 kolona varsayılan değer verdiği için yeni
     * migration'lar onu yazmaz. Backend_Architecture §11 "Eski migration'ların kullandığı kolonlar".
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "admin_only", nullable = false)
    private boolean legacyAdminOnly;
}
