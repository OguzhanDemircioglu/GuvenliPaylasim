package com.server.modules.user.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.user.UserMessages;
import jakarta.validation.constraints.NotBlank;

/**
 * Hesap silme onayı. {@code password}: istemcinin paroladan türettiği authSecret. {@code totpCode}: iki adımlı
 * doğrulama açıksa Authenticator ya da kurtarma kodu.
 */
public record DeleteAccountRequest(
        @NotBlank(message = UserMessages.PASSWORD_REQUIRED)
        @MaxBytes(value = 72, message = UserMessages.PASSWORD_TOO_LONG)
        String password,

        String totpCode) {
}
