package com.server.modules.category.dto;

import com.server.modules.category.CategoryMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank(message = CategoryMessages.NAME_REQUIRED)
        @Size(max = 100, message = CategoryMessages.NAME_TOO_LONG)
        String name,

        @Size(max = 500, message = CategoryMessages.DESCRIPTION_TOO_LONG)
        String description) {
}
