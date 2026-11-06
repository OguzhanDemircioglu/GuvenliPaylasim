package com.server.modules.invite.dto;

import com.server.modules.document.DocumentKind;
import com.server.modules.document.DocumentMessages;
import com.server.modules.invite.InviteMessages;
import com.server.modules.user.Phones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Davet koduyla kilitli kutu (Crypto_Design §7.6, gönderen adım 4). Kod, kodKey ve mühürKey bu istekte yoktur.
 *
 * @param recipientPhone {@code 5XXXXXXXXX}
 * @param ttlHours       1, 24, 72 ya da 168 (en fazla 7 gün)
 * @param wrapped        {@code 0x01 | AES-GCM(kodKey, K || gönderen parmak izi)}, Base64
 * @param verifierHash   {@code SHA-256(doğrulayıcı)}, 64 karakter hex
 * @param selfWrapped    mühürKey'in gönderenin kendi açık anahtarına sarmalanmış hali, Base64
 */
public record InviteCreateRequest(
        @NotBlank(message = InviteMessages.PHONE_REQUIRED)
        @Pattern(regexp = Phones.INPUT_PATTERN, message = InviteMessages.PHONE_INVALID)
        String recipientPhone,

        @NotNull(message = InviteMessages.TTL_INVALID)
        Integer ttlHours,

        @NotNull(message = DocumentMessages.KIND_REQUIRED)
        DocumentKind kind,

        @NotBlank(message = DocumentMessages.PAYLOAD_REQUIRED)
        @Size(max = 14_000_000, message = DocumentMessages.PAYLOAD_TOO_LARGE)
        String payload,

        @NotBlank(message = DocumentMessages.SIGNATURE_REQUIRED)
        @Size(max = 200, message = DocumentMessages.SIGNATURE_TOO_LONG)
        String signature,

        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Size(max = 200, message = InviteMessages.FIELD_TOO_LONG)
        String wrapped,

        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Pattern(regexp = "^[0-9a-f]{64}$", message = InviteMessages.VERIFIER_INVALID)
        String verifierHash,

        @NotBlank(message = InviteMessages.FIELD_REQUIRED)
        @Size(max = 300, message = InviteMessages.FIELD_TOO_LONG)
        String selfWrapped) {

    public InviteCreateRequest {
        recipientPhone = Phones.trim(recipientPhone);
    }
}
