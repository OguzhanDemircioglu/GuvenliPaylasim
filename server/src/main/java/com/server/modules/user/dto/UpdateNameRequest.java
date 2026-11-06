package com.server.modules.user.dto;

import com.server.modules.user.FullNames;
import com.server.modules.user.UserMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Profilden ad soyad değişikliği. Telefon değiştirilemez (parola telefonla türetilir, Crypto_Design §7.3). */
public record UpdateNameRequest(
        @NotBlank(message = UserMessages.FULL_NAME_REQUIRED)
        @Size(max = FullNames.MAX_LENGTH, message = UserMessages.FULL_NAME_TOO_LONG)
        @Pattern(regexp = FullNames.PATTERN, message = UserMessages.FULL_NAME_INVALID)
        String fullName) {

    public UpdateNameRequest {
        fullName = FullNames.trim(fullName);
    }
}
