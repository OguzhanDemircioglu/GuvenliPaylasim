package com.server.modules.auth.dto;

import com.server.modules.auth.AuthMessages;
import com.server.modules.user.Phones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code phone} {@code 5XXXXXXXXX}; {@code password} istemcinin türettiği authSecret'tır. */
public record LoginRequest(
        @NotBlank(message = AuthMessages.PHONE_REQUIRED)
        @Pattern(regexp = Phones.INPUT_PATTERN, message = AuthMessages.PHONE_INVALID)
        String phone,

        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        String password) {

    public LoginRequest {
        phone = Phones.trim(phone);
    }
}
