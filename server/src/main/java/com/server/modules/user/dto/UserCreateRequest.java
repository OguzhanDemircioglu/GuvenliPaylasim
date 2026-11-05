package com.server.modules.user.dto;

import com.server.common.validation.MaxBytes;
import com.server.modules.user.Emails;
import com.server.modules.user.UserMessages;
import com.server.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = UserMessages.EMAIL_REQUIRED)
        @Email(message = UserMessages.EMAIL_INVALID)
        @Size(max = 254, message = UserMessages.EMAIL_INVALID)
        String email,

        @NotBlank(message = UserMessages.FULL_NAME_REQUIRED)
        @Size(max = 100, message = UserMessages.FULL_NAME_TOO_LONG)
        String fullName,

        // bcrypt en fazla 72 byte'ı işler; Türkçe harfler 2 byte olduğu için sınır byte olarak kontrol edilir.
        @NotBlank(message = UserMessages.PASSWORD_REQUIRED)
        @Size(min = 8, message = UserMessages.PASSWORD_TOO_SHORT)
        @MaxBytes(value = 72, message = UserMessages.PASSWORD_TOO_LONG)
        String password,

        @NotNull(message = UserMessages.ROLE_REQUIRED)
        Role role) {

    public UserCreateRequest {
        email = Emails.normalize(email);
    }
}
