package com.server.modules.user.dto;

import com.server.common.message.GlobalMessages;
import com.server.common.service.VersionedRequest;
import com.server.modules.user.Emails;
import com.server.modules.user.UserMessages;
import com.server.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = UserMessages.EMAIL_REQUIRED)
        @Email(message = UserMessages.EMAIL_INVALID)
        @Size(max = 254, message = UserMessages.EMAIL_INVALID)
        String email,

        @NotBlank(message = UserMessages.FULL_NAME_REQUIRED)
        @Size(max = 100, message = UserMessages.FULL_NAME_TOO_LONG)
        String fullName,

        @NotNull(message = UserMessages.ROLE_REQUIRED)
        Role role,

        @NotNull(message = UserMessages.ACTIVE_REQUIRED)
        Boolean active,

        @NotNull(message = GlobalMessages.VERSION_REQUIRED)
        Long version) implements VersionedRequest {

    public UserUpdateRequest {
        email = Emails.normalize(email);
    }
}
