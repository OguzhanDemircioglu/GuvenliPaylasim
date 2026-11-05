package com.server.modules.category.dto;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
