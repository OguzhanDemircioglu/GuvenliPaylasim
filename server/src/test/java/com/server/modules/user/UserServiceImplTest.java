package com.server.modules.user;

import com.server.common.exception.BusinessException;
import com.server.modules.auth.UserSessionRepository;
import com.server.modules.key.UserKeyRepository;
import com.server.modules.unit.UnitRepository;
import com.server.modules.user.dto.UserCreateRequest;
import com.server.modules.user.dto.UserPasswordResetRequest;
import com.server.modules.user.dto.UserUpdateRequest;
import com.server.security.CurrentUser;
import com.server.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final Long UNIT_ID = 10L;

    @Mock
    private UserRepository repository;
    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private UserKeyRepository keyRepository;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository, new UserMapperImpl(), sessionRepository, passwordEncoder, currentUser,
                unitRepository, keyRepository);
    }

    @Test
    void lastActiveSuperadminCannotBeDeactivated() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(user(1L, Role.SUPERADMIN, true)));
        when(repository.countByRoleAndActiveTrue(Role.SUPERADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L,
                new UserUpdateRequest("user1@firma.com", "Root", Role.SUPERADMIN, false, null, null, 0L)))
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
                new UserCreateRequest("o@firma.com", "Owner", "Owner12345", Role.OWNER, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.ROLE_NOT_ASSIGNABLE);
    }

    @Test
    void roleChangeRevokesAllSessions() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);
        User user = user(4L, Role.USER, true);
        when(repository.findById(4L)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        service.update(4L, new UserUpdateRequest("user4@firma.com", "Ayşe", Role.OWNER, true, null, null, 0L));

        assertThat(user.getRole()).isEqualTo(Role.OWNER);
        verify(sessionRepository).revokeAllByUserId(eq(4L), any());
    }

    @Test
    void deactivatingUserRevokesAllSessions() {
        when(currentUser.role()).thenReturn(Role.ADMIN);
        User user = user(2L, Role.USER, true);
        when(unitRepository.existsById(UNIT_ID)).thenReturn(true);
        when(repository.findById(2L)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);

        service.update(2L, new UserUpdateRequest("user2@firma.com", "Ali", Role.USER, false, UNIT_ID, null, 0L));

        assertThat(user.isActive()).isFalse();
        verify(sessionRepository).revokeAllByUserId(eq(2L), any());
    }

    @Test
    void registerCreatesActiveUserThatKeepsOwnPassword() {
        when(unitRepository.existsById(UNIT_ID)).thenReturn(true);
        when(passwordEncoder.encode("secret-derived")).thenReturn("hash");

        service.register(" Yeni@Firma.com ", "Yeni Kişi", "secret-derived", UNIT_ID, "5551234567");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("yeni@firma.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getUnitId()).isEqualTo(UNIT_ID);
        assertThat(user.getPassword()).isEqualTo("hash");
        assertThat(user.isActive()).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
    }

    @Test
    void registerRejectsExistingEmail() {
        when(repository.existsByEmail("var@firma.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("var@firma.com", "Var", "secret-derived", UNIT_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.EMAIL_EXISTS);
    }

    @Test
    void registerRejectsUnknownUnit() {
        when(unitRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.register("u@firma.com", "U", "secret-derived", 99L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.UNIT_NOT_FOUND);
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

    @Test
    void passwordResetRemovesKeysForcesPasswordChangeAndRevokesSessions() {
        when(currentUser.role()).thenReturn(Role.ADMIN);
        User user = user(5L, Role.USER, true);
        when(repository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Yeni12345")).thenReturn("new-hash");

        service.resetPassword(5L, new UserPasswordResetRequest("Yeni12345"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.isMustChangePassword()).isTrue();
        // Özel anahtarlar eski parolayla şifreliydi; kullanıcı ilk girişte yenilerini üretir.
        verify(keyRepository).deleteById(5L);
        verify(sessionRepository).revokeAllByUserId(eq(5L), any());
    }

    @Test
    void adminCannotResetAnotherAdminsPassword() {
        when(currentUser.role()).thenReturn(Role.ADMIN);
        when(repository.findById(6L)).thenReturn(Optional.of(user(6L, Role.ADMIN, true)));

        assertThatThrownBy(() -> service.resetPassword(6L, new UserPasswordResetRequest("Yeni12345")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.NOT_MANAGEABLE);
        verify(keyRepository, never()).deleteById(any());
    }

    @Test
    void emailCannotChangeBecauseItSaltsTheKeys() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);
        when(repository.findById(4L)).thenReturn(Optional.of(user(4L, Role.USER, true)));

        assertThatThrownBy(() -> service.update(4L,
                new UserUpdateRequest("baska@firma.com", "Ayşe", Role.USER, true, UNIT_ID, null, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.EMAIL_IMMUTABLE);
    }

    @Test
    void userRoleNeedsUnitButAdminRolesDoNot() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);

        assertThatThrownBy(() -> service.create(
                new UserCreateRequest("u@firma.com", "U", "User12345", Role.USER, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.UNIT_REQUIRED);

        when(passwordEncoder.encode("Admin12345")).thenReturn("hash");
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.create(new UserCreateRequest("a@firma.com", "A", "Admin12345", Role.ADMIN, null, null));
        verify(repository).saveAndFlush(any(User.class));
    }

    @Test
    void superadminRoleCanNeitherBeGrantedNorTaken() {
        when(currentUser.role()).thenReturn(Role.SUPERADMIN);

        assertThatThrownBy(() -> service.create(
                new UserCreateRequest("s@firma.com", "S", "Super12345", Role.SUPERADMIN, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.ROLE_NOT_ASSIGNABLE);

        when(repository.findById(1L)).thenReturn(Optional.of(user(1L, Role.SUPERADMIN, true)));
        assertThatThrownBy(() -> service.update(1L,
                new UserUpdateRequest("user1@firma.com", "Root", Role.ADMIN, true, null, null, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.ROLE_NOT_ASSIGNABLE);
    }

    @Test
    void adminCannotChangeRoles() {
        when(currentUser.role()).thenReturn(Role.ADMIN);
        when(repository.findById(4L)).thenReturn(Optional.of(user(4L, Role.USER, true)));

        assertThatThrownBy(() -> service.update(4L,
                new UserUpdateRequest("user4@firma.com", "Ayşe", Role.ADMIN, true, UNIT_ID, null, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.ROLE_NOT_ASSIGNABLE);
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
