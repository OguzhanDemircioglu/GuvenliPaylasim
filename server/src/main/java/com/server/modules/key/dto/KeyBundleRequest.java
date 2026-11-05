package com.server.modules.key.dto;

import com.server.modules.key.KeyMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeyBundleRequest(
        @NotBlank(message = KeyMessages.PUBLIC_KEY_REQUIRED)
        @Size(max = 200, message = KeyMessages.PUBLIC_KEY_INVALID)
        String encryptionPublicKey,

        @NotBlank(message = KeyMessages.PUBLIC_KEY_REQUIRED)
        @Size(max = 200, message = KeyMessages.PUBLIC_KEY_INVALID)
        String signingPublicKey,

        @NotBlank(message = KeyMessages.BUNDLE_REQUIRED)
        @Size(max = 1000, message = KeyMessages.BUNDLE_TOO_LONG)
        String privateKeyBundle) {
}
