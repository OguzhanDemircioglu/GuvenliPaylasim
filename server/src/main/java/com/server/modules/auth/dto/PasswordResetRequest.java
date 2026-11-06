package com.server.modules.auth.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.auth.AuthMessages;
import com.server.modules.key.dto.KeyBundleRequest;
import com.server.modules.user.Phones;
import com.server.modules.user.UserMessages;
import com.server.modules.verification.VerificationMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Parola sıfırlamanın tamamlanması (docs/Crypto_Design.md §7.5). Eski özel anahtarlar eski parolayla kilitli olduğu
 * için istemci yeni anahtar çifti üretip {@code keys} ile gönderir.
 */
public record PasswordResetRequest(
        @NotBlank(message = UserMessages.PHONE_REQUIRED)
        @Pattern(regexp = Phones.INPUT_PATTERN, message = UserMessages.PHONE_INVALID)
        String phone,

        @NotBlank(message = VerificationMessages.CODE_REQUIRED)
        @Pattern(regexp = "^\\d{6}$", message = VerificationMessages.CODE_INVALID)
        String code,

        @NotBlank(message = AuthMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = AuthMessages.NEW_PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = AuthMessages.NEW_PASSWORD_TOO_LONG)
        String password,

        @NotNull(message = AuthMessages.KEYS_REQUIRED)
        @Valid
        KeyBundleRequest keys,

        /** TOTP açıksa zorunlu: Authenticator kodu ya da kurtarma kodu (SIM kopyalamaya karşı, Security.md S5). */
        @Size(max = 20, message = AuthMessages.TOTP_INVALID)
        String totpCode) {

    public PasswordResetRequest {
        phone = Phones.trim(phone);
    }
}
