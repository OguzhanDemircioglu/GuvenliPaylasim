package com.server.modules.auth.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.user.Emails;
import com.server.modules.user.UserMessages;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Herkese açık kayıt. Bilerek rol alanı yoktur: kayıt olan herkes USER olur. Kurallar UserCreateRequest ile aynıdır. */
public record RegisterRequest(
        @NotBlank(message = UserMessages.FULL_NAME_REQUIRED)
        @Size(max = 100, message = UserMessages.FULL_NAME_TOO_LONG)
        String fullName,

        @NotBlank(message = UserMessages.EMAIL_REQUIRED)
        @Email(message = UserMessages.EMAIL_INVALID)
        @Size(max = 254, message = UserMessages.EMAIL_INVALID)
        String email,

        @NotBlank(message = UserMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = UserMessages.PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = UserMessages.PASSWORD_TOO_LONG)
        String password) {

    public RegisterRequest {
        email = Emails.normalize(email);
    }
}
