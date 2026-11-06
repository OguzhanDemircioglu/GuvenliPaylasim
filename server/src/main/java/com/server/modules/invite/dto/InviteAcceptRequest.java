package com.server.modules.invite.dto;

import com.server.modules.document.DocumentMessages;
import com.server.modules.invite.InviteMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alıcı kodu açtıktan sonra (Crypto_Design §7.6, alıcı adım 4–6).
 *
 * @param wrappedKey  K'nın alıcının kendi açık anahtarına sarmalanmış hali (§4.2)
 * @param seal        kabul mührü, Base64
 * @param fingerprint alıcının doğruladığı, gönderenin parmak izi
 * @param signature   alıcının kişi kaydı imzası (ContactResponse)
 */
public record InviteAcceptRequest(
        @NotBlank(message = DocumentMessages.WRAPPED_KEY_REQUIRED)
        @Size(max = 300, message = DocumentMessages.WRAPPED_KEY_TOO_LONG)
        String wrappedKey,

        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Size(max = 100, message = InviteMessages.FIELD_TOO_LONG)
        String seal,

        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Pattern(regexp = "^([0-9A-F]{4} ){7}[0-9A-F]{4}$", message = InviteMessages.FIELD_TOO_LONG)
        String fingerprint,

        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Size(max = 200, message = InviteMessages.FIELD_TOO_LONG)
        String signature) {
}
