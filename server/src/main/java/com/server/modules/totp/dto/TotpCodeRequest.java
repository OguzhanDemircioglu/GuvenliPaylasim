package com.server.modules.totp.dto;

import com.server.modules.totp.TotpMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TotpCodeRequest(
        @NotBlank(message = TotpMessages.CODE_REQUIRED)
        @Size(max = 20, message = TotpMessages.CODE_INVALID)
        String code) {
}
