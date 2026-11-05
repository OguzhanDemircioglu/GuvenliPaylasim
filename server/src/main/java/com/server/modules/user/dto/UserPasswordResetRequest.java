package com.server.modules.user.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.user.UserMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordResetRequest(
        @NotBlank(message = UserMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = UserMessages.PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = UserMessages.PASSWORD_TOO_LONG)
        String newPassword) {
}
