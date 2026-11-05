package com.server.modules.auth.dto;

import com.server.modules.auth.AuthMessages;
import com.server.modules.user.Emails;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = AuthMessages.EMAIL_REQUIRED)
        String email,

        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        String password) {

    public LoginRequest {
        email = Emails.normalize(email);
    }
}
