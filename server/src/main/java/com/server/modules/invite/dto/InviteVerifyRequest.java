package com.server.modules.invite.dto;

import com.server.modules.invite.InviteMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Gönderen mührü doğruladı: alıcının parmak izi ve gönderenin kişi kaydı imzası. */
public record InviteVerifyRequest(
        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Pattern(regexp = "^([0-9A-F]{4} ){7}[0-9A-F]{4}$", message = InviteMessages.FIELD_TOO_LONG)
        String fingerprint,

        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Size(max = 200, message = InviteMessages.FIELD_TOO_LONG)
        String signature) {
}
