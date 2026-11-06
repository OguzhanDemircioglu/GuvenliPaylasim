package com.server.modules.invite.dto;

import com.server.modules.invite.InviteMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code verifier}: koddan türetilen doğrulayıcı (32 bayt), Base64. Sunucu SHA-256'sını arar. */
public record InviteRedeemRequest(
        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Size(max = 64, message = InviteMessages.VERIFIER_INVALID)
        String verifier) {
}
