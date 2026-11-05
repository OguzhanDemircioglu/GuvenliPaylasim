package com.server.modules.unit.dto;

import java.time.Instant;

public record UnitResponse(
        Long id,
        String name,
        String description,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
