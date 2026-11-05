package com.server.modules.auth.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.auth.AuthMessages;
import com.server.modules.key.dto.KeyBundleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Parolalar istemcinin türettiği değerlerdir (AuthSecrets). Özel anahtarlar eski parolayla şifreli olduğu için
 * istemci onları yeni parolayla yeniden şifreleyip {@code keys} ile gönderir; ikisi aynı transaction'da kaydedilir.
 */
public record ChangePasswordRequest(
        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        String currentPassword,

        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = AuthMessages.NEW_PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = AuthMessages.NEW_PASSWORD_TOO_LONG)
        String newPassword,

        @NotNull(message = AuthMessages.KEYS_REQUIRED)
        @Valid
        KeyBundleRequest keys) {
}
