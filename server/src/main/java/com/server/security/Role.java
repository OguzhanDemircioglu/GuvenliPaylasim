package com.server.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * Sistemdeki roller. Her kullanıcının tek rolü vardır. Hiyerarşi (docs/Security_Roadmap.md §4.1):
 * <pre>
 *   SUPERADMIN ─┬─ ADMIN ── USER
 *               └─ OWNER
 * </pre>
 * Üstteki rol alttakinin bütün yetkilerini taşır. OWNER bilerek ADMIN'in yanındadır, üstünde değil:
 * salt okunurdur ve ADMIN/USER yetkilerini miras almaz.
 * <p>
 * Bu tanım tek kaynaktır: Spring Security'nin RoleHierarchy'si (SecurityConfig) ve menü süzme buradan üretilir.
 * Endpoint'ler @PreAuthorize("hasRole('ADMIN')") gibi en düşük gereken rolle işaretlenir.
 */
public enum Role {
    SUPERADMIN,
    ADMIN,
    OWNER,
    USER;

    /** Bu rolün doğrudan kapsadığı roller. */
    public Set<Role> implies() {
        return switch (this) {
            case SUPERADMIN -> EnumSet.of(ADMIN, OWNER);
            case ADMIN -> EnumSet.of(USER);
            case OWNER, USER -> EnumSet.noneOf(Role.class);
        };
    }

    /** Bu rolün kendisi ve dolaylı olarak kapsadığı bütün roller. */
    public Set<Role> reachable() {
        Set<Role> result = EnumSet.of(this);
        for (Role implied : implies()) {
            result.addAll(implied.reachable());
        }
        return result;
    }

    /** Bu rol {@code other} rolünün yetkilerini taşıyor mu? */
    public boolean includes(Role other) {
        return reachable().contains(other);
    }
}
