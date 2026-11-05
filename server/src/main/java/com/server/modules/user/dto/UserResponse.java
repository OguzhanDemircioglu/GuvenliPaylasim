package com.server.modules.user.dto;

import com.server.security.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
