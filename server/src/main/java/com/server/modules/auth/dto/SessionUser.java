package com.server.modules.auth.dto;

import com.server.modules.user.User;
import com.server.security.Role;

public record SessionUser(Long id, String email, String fullName, Role role, Long unitId, boolean mustChangePassword) {

    public static SessionUser from(User user) {
        return new SessionUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getUnitId(), user.isMustChangePassword());
    }
}
