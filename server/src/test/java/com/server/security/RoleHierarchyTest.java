package com.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rol hiyerarşisi: SUPERADMIN ─┬─ ADMIN ── USER, └─ OWNER. OWNER salt okunurdur; ADMIN/USER yetkisi miras almaz.
 * Hem Role enum'u hem Spring Security'ye verilen RoleHierarchy aynı sonucu vermelidir.
 */
class RoleHierarchyTest {

    private final RoleHierarchy hierarchy = SecurityConfig.roleHierarchy();

    @Test
    void superadminInheritsEveryRole() {
        assertThat(reachable(Role.SUPERADMIN)).containsExactlyInAnyOrder("SUPERADMIN", "ADMIN", "OWNER", "USER");
        assertThat(Role.SUPERADMIN.reachable()).containsExactlyInAnyOrder(Role.values());
    }

    @Test
    void adminInheritsUserButNotOwner() {
        assertThat(reachable(Role.ADMIN)).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(Role.ADMIN.includes(Role.USER)).isTrue();
        assertThat(Role.ADMIN.includes(Role.OWNER)).isFalse();
    }

    @Test
    void ownerAndUserInheritNothing() {
        assertThat(reachable(Role.OWNER)).containsExactly("OWNER");
        assertThat(reachable(Role.USER)).containsExactly("USER");
        assertThat(Role.OWNER.includes(Role.USER)).isFalse();
        assertThat(Role.USER.includes(Role.ADMIN)).isFalse();
    }

    @Test
    void springHierarchyMatchesRoleEnum() {
        for (Role role : Role.values()) {
            assertThat(reachable(role))
                    .isEqualTo(role.reachable().stream().map(Role::name).collect(Collectors.toSet()));
        }
    }

    private Set<String> reachable(Role role) {
        return hierarchy.getReachableGrantedAuthorities(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }
}
