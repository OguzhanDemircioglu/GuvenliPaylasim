package com.server.modules.auth.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.user.Phones;
import com.server.modules.user.UserMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Herkese açık kayıt (docs/Crypto_Design.md §7.2): ad soyad, telefon, parola. {@code password} istemcinin paroladan
 * türettiği authSecret'tır (AuthSecrets).
 */
public record RegisterRequest(
        @NotBlank(message = UserMessages.FULL_NAME_REQUIRED)
        @Size(max = 100, message = UserMessages.FULL_NAME_TOO_LONG)
        String fullName,

        @NotBlank(message = UserMessages.PHONE_REQUIRED)
        @Pattern(regexp = Phones.INPUT_PATTERN, message = UserMessages.PHONE_INVALID)
        String phone,

        @NotBlank(message = UserMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = UserMessages.PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = UserMessages.PASSWORD_TOO_LONG)
        String password) {

    public RegisterRequest {
        phone = Phones.trim(phone);
    }
}
