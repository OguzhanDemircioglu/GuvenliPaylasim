package com.server.modules.user;

import com.server.common.exception.BusinessException;
import com.server.common.service.BaseServiceImpl;
import com.server.modules.auth.UserSessionRepository;
import com.server.modules.key.UserKeyRepository;
import com.server.modules.unit.UnitRepository;
import com.server.modules.user.dto.UserCreateRequest;
import com.server.modules.user.dto.UserPasswordResetRequest;
import com.server.modules.user.dto.UserResponse;
import com.server.modules.user.dto.UserUpdateRequest;
import com.server.security.CurrentUser;
import com.server.security.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Yönetim kapsamı (docs/Security_Roadmap.md §4.1, §4.7):
 * <ul>
 *   <li>ADMIN sadece USER'ları yönetir; kimsenin rolünü değiştiremez.</li>
 *   <li>SUPERADMIN herkesi yönetir; USER, ADMIN ve OWNER rolleri arasında geçiş yapabilir.</li>
 *   <li>SUPERADMIN rolü API'den verilemez ve alınamaz (sadece migration ile oluşur).</li>
 *   <li>Son aktif SUPERADMIN silinemez ve pasife alınamaz.</li>
 * </ul>
 */
@Service
public class UserServiceImpl extends BaseServiceImpl<User, UserCreateRequest, UserUpdateRequest, UserResponse>
        implements UserService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    private final UnitRepository unitRepository;
    private final UserKeyRepository keyRepository;

    public UserServiceImpl(UserRepository repository, UserMapper mapper, UserSessionRepository sessionRepository,
                           PasswordEncoder passwordEncoder, CurrentUser currentUser,
                           UnitRepository unitRepository, UserKeyRepository keyRepository) {
        super(repository, mapper);
        this.userRepository = repository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.unitRepository = unitRepository;
        this.keyRepository = keyRepository;
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (!assignableRoles().contains(request.role())) {
            throw new BusinessException(UserMessages.ROLE_NOT_ASSIGNABLE);
        }
        return super.create(request);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findEntity(id);
        Role previousRole = user.getRole();
        UserResponse response = super.update(id, request);
        // Pasife alınan ya da rolü değişen kullanıcı yeniden giriş yapar; yeni rol yeni token'la gelir.
        if (!request.active() || previousRole != request.role()) {
            sessionRepository.revokeAllByUserId(id, Instant.now());
        }
        return response;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        super.delete(id);
        sessionRepository.revokeAllByUserId(id, Instant.now());
    }

    @Override
    @Transactional
    public void resetPassword(Long id, UserPasswordResetRequest request) {
        User user = findEntity(id);
        checkManageable(user);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(true);
        // Özel anahtarlar eski parolayla şifreliydi; yönetici onları açamaz. Kullanıcı yeni anahtar üretir ve
        // eski belgelerini artık açamaz (uçtan uca şifrelemenin doğal sonucu, docs/Crypto_Design.md §6).
        keyRepository.deleteById(id);
        sessionRepository.revokeAllByUserId(id, Instant.now());
    }

    @Override
    protected void validateCreate(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserMessages.EMAIL_EXISTS);
        }
        validateUnit(request.role(), request.unitId());
    }

    @Override
    protected void prepareCreate(User entity, UserCreateRequest request) {
        entity.setPassword(passwordEncoder.encode(request.password()));
    }

    @Override
    protected void validateUpdate(User entity, UserUpdateRequest request) {
        checkManageable(entity);
        if (entity.getRole() != request.role()) {
            Set<Role> assignable = assignableRoles();
            if (!assignable.contains(entity.getRole()) || !assignable.contains(request.role())) {
                throw new BusinessException(UserMessages.ROLE_NOT_ASSIGNABLE);
            }
        }
        if (!entity.getEmail().equals(request.email())) {
            throw new BusinessException(UserMessages.EMAIL_IMMUTABLE);
        }
        validateUnit(request.role(), request.unitId());
        if (!request.active() && isLastActiveSuperadmin(entity)) {
            throw new BusinessException(UserMessages.LAST_SUPERADMIN);
        }
    }

    @Override
    protected void validateDelete(User entity) {
        checkManageable(entity);
        if (isLastActiveSuperadmin(entity)) {
            throw new BusinessException(UserMessages.LAST_SUPERADMIN);
        }
    }

    /** Belge alıp gönderen her USER bir birime bağlıdır; yönetici rollerinde birim isteğe bağlıdır. */
    private void validateUnit(Role role, Long unitId) {
        if (unitId == null) {
            if (role == Role.USER) {
                throw new BusinessException(UserMessages.UNIT_REQUIRED);
            }
            return;
        }
        if (!unitRepository.existsById(unitId)) {
            throw new BusinessException(UserMessages.UNIT_NOT_FOUND);
        }
    }

    /** İşlemi yapan kullanıcının bu kullanıcıyı değiştirme/silme yetkisi var mı? */
    private void checkManageable(User target) {
        boolean manageable = switch (currentUser.role()) {
            case SUPERADMIN -> true;
            case ADMIN -> target.getRole() == Role.USER;
            case OWNER, USER -> false;
        };
        if (!manageable) {
            throw new BusinessException(UserMessages.NOT_MANAGEABLE);
        }
    }

    /** İşlemi yapan kullanıcının verebileceği roller. SUPERADMIN hiçbir zaman verilemez. */
    private Set<Role> assignableRoles() {
        return switch (currentUser.role()) {
            case SUPERADMIN -> EnumSet.of(Role.ADMIN, Role.OWNER, Role.USER);
            case ADMIN -> EnumSet.of(Role.USER);
            case OWNER, USER -> EnumSet.noneOf(Role.class);
        };
    }

    /** Süperadmin kalmazsa adminleri ve sistemi yönetecek kimse kalmaz; son aktif süperadmin korunur. */
    private boolean isLastActiveSuperadmin(User user) {
        return user.getRole() == Role.SUPERADMIN && user.isActive()
                && userRepository.countByRoleAndActiveTrue(Role.SUPERADMIN) <= 1;
    }
}
