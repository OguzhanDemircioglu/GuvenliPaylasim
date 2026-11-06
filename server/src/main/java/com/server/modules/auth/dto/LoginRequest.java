package com.server.modules.auth.dto;

import com.server.modules.auth.AuthMessages;
import com.server.modules.user.Phones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code phone} {@code 5XXXXXXXXX}; {@code password} istemcinin türettiği authSecret'tır. {@code totpCode}: TOTP açıksa
 * Authenticator kodu ya da kurtarma kodu; kapalıysa yok sayılır.
 */
public record LoginRequest(
        @NotBlank(message = AuthMessages.PHONE_REQUIRED)
        @Pattern(regexp = Phones.INPUT_PATTERN, message = AuthMessages.PHONE_INVALID)
        String phone,

        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        String password,

        @Size(max = 20, message = AuthMessages.TOTP_INVALID)
        String totpCode) {

    public LoginRequest {
        phone = Phones.trim(phone);
    }
}
