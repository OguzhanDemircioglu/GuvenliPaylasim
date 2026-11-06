package com.server.modules.totp.dto;

import com.server.modules.auth.AuthMessages;
import com.server.modules.totp.TotpMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Kapatmak için mevcut parola (authSecret) ve Authenticator ya da kurtarma kodu gerekir. */
public record TotpDisableRequest(
        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        String password,

        @NotBlank(message = TotpMessages.CODE_REQUIRED)
        @Size(max = 20, message = TotpMessages.CODE_INVALID)
        String code) {
}
