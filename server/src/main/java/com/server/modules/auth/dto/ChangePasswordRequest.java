package com.server.modules.auth.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.auth.AuthMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        String currentPassword,

        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = AuthMessages.NEW_PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = AuthMessages.NEW_PASSWORD_TOO_LONG)
        String newPassword) {
}
