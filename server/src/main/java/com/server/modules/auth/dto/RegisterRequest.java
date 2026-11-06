package com.server.modules.auth.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.auth.AuthMessages;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.user.FullNames;
import com.server.modules.user.Phones;
import com.server.modules.user.UserMessages;
import com.server.modules.verification.VerificationMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kaydın tamamlanması (docs/Crypto_Design.md §7.2): WhatsApp ile gelen kod, ad soyad, parola ve kayıtta istemcide
 * üretilen anahtarlar. {@code password} istemcinin paroladan türettiği authSecret'tır (AuthSecrets).
 */
public record RegisterRequest(
        @NotBlank(message = UserMessages.PHONE_REQUIRED)
        @Pattern(regexp = Phones.INPUT_PATTERN, message = UserMessages.PHONE_INVALID)
        String phone,

        @NotBlank(message = VerificationMessages.CODE_REQUIRED)
        @Pattern(regexp = "^\\d{6}$", message = VerificationMessages.CODE_INVALID)
        String code,

        @NotBlank(message = UserMessages.FULL_NAME_REQUIRED)
        @Size(max = FullNames.MAX_LENGTH, message = UserMessages.FULL_NAME_TOO_LONG)
        @Pattern(regexp = FullNames.PATTERN, message = UserMessages.FULL_NAME_INVALID)
        String fullName,

        @NotBlank(message = UserMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = UserMessages.PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = UserMessages.PASSWORD_TOO_LONG)
        String password,

        @NotNull(message = AuthMessages.KEYS_REQUIRED)
        @Valid
        KeyBundleRequest keys) {

    public RegisterRequest {
        phone = Phones.trim(phone);
        fullName = FullNames.trim(fullName);
    }
}
