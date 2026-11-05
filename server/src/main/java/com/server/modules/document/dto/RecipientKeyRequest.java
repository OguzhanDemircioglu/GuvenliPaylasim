package com.server.modules.document.dto;

import com.server.modules.document.DocumentMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Belge anahtarının bu alıcının açık anahtarıyla sarmalanmış hali. */
public record RecipientKeyRequest(
        @NotNull(message = DocumentMessages.RECIPIENT_REQUIRED)
        Long userId,

        @NotBlank(message = DocumentMessages.WRAPPED_KEY_REQUIRED)
        @Size(max = 300, message = DocumentMessages.WRAPPED_KEY_TOO_LONG)
        String wrappedKey) {
}
