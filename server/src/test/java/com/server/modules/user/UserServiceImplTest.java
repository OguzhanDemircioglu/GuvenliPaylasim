package com.server.modules.user;

import com.server.common.exception.BusinessException;
import com.server.modules.auth.UserSessionRepository;
import com.server.modules.user.dto.UserCreateRequest;
import com.server.modules.user.dto.UserUpdateRequest;
import com.server.security.CurrentUser;
import com.server.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository repository;
    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CurrentUser currentUser;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository, new UserMapperImpl(), sessionRepository, passwordEncoder, currentUser);
    }

    @Test
    void lastActiveSuperadminCannotBeDeactivated() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(user(1L, Role.SUPERADMIN, true)));
        when(repository.countByRoleAndActiveTrue(Role.SUPERADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L,
                new UserUpdateRequest("root@firma.com", "Root", Role.SUPERADMIN, false, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.LAST_SUPERADMIN);
    }

    @Test
    void lastActiveSuperadminCannotBeDeleted() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(user(1L, Role.SUPERADMIN, true)));
        when(repository.countByRoleAndActiveTrue(Role.SUPERADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.LAST_SUPERADMIN);
    }

    @Test
    void adminCannotManageAnotherAdmin() {
        when(currentUser.role()).thenReturn(Role.ADMIN);
        when(repository.findById(3L)).thenReturn(Optional.of(user(3L, Role.ADMIN, true)));

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.NOT_MANAGEABLE);
    }

    @Test
    void adminCannotCreateOwner() {
        when(currentUser.role()).thenReturn(Role.ADMIN);

        assertThatThrownBy(() -> service.create(
                new UserCreateRequest("o@firma.com", "Owner", "Owner12345", Role.OWNER)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.ROLE_NOT_ASSIGNABLE);
    }

    @Test
    void roleChangeRevokesAllSessions() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);
        User user = user(4L, Role.USER, true);
        when(repository.findById(4L)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        service.update(4L, new UserUpdateRequest("ayse@firma.com", "Ayşe", Role.OWNER, true, 0L));

        assertThat(user.getRole()).isEqualTo(Role.OWNER);
        verify(sessionRepository).revokeAllByUserId(eq(4L), any());
    }

    @Test
    void deactivatingUserRevokesAllSessions() {
        when(currentUser.role()).thenReturn(Role.ADMIN);
        User user = user(2L, Role.USER, true);
        when(repository.findById(2L)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        service.update(2L, new UserUpdateRequest("ali@firma.com", "Ali", Role.USER, false, 0L));

        assertThat(user.isActive()).isFalse();
        verify(sessionRepository).revokeAllByUserId(eq(2L), any());
    }

    @Test
    void emailIsNormalizedIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Emails.normalize("  ADMIN@FIRMA.COM ")).isEqualTo("admin@firma.com");
            assertThat(Emails.normalize("İsmail@firma.com")).isEqualTo("ismail@firma.com");
            assertThat(Emails.normalize("admin@test.local".toUpperCase())).isEqualTo("admin@test.local");
        } finally {
            Locale.setDefault(original);
        }
    }

    private static User user(Long id, Role role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@firma.com");
        user.setFullName("User " + id);
        user.setRole(role);
        user.setActive(active);
        user.setVersion(0L);
        return user;
    }
}
