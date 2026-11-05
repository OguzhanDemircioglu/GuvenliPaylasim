package com.server.modules.category.dto;

import com.server.common.message.GlobalMessages;
import com.server.common.service.VersionedRequest;
import com.server.modules.category.CategoryMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = CategoryMessages.NAME_REQUIRED)
        @Size(max = 100, message = CategoryMessages.NAME_TOO_LONG)
        String name,

        @Size(max = 500, message = CategoryMessages.DESCRIPTION_TOO_LONG)
        String description,

        @NotNull(message = GlobalMessages.VERSION_REQUIRED)
        Long version) implements VersionedRequest {
}
