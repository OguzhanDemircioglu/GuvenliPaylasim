package com.server.modules.auth.dto;

import com.server.modules.user.Phones;
import com.server.modules.user.UserMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Doğrulama kodu isteği (kayıt ve parola sıfırlama başlangıcı). {@code phone}: {@code 5XXXXXXXXX}. */
public record PhoneRequest(
        @NotBlank(message = UserMessages.PHONE_REQUIRED)
        @Pattern(regexp = Phones.INPUT_PATTERN, message = UserMessages.PHONE_INVALID)
        String phone) {

    public PhoneRequest {
        phone = Phones.trim(phone);
    }
}
