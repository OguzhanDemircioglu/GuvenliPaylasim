package com.server.modules.unit.dto;

import com.server.modules.unit.UnitMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnitCreateRequest(
        @NotBlank(message = UnitMessages.NAME_REQUIRED)
        @Size(max = 100, message = UnitMessages.NAME_TOO_LONG)
        String name,

        @Size(max = 500, message = UnitMessages.DESCRIPTION_TOO_LONG)
        String description) {
}
