package com.server.modules.key.dto;

import com.server.modules.key.KeyMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code privateKeyBundle} gerçekte yaklaşık 170 karakterdir. 700 sınırı, sunucuda ikinci kilitle (ServerSeal) büyüyen
 * değerin 1000 karakterlik kolona sığmasını garanti eder.
 */
public record KeyBundleRequest(
        @NotBlank(message = KeyMessages.PUBLIC_KEY_REQUIRED)
        @Size(max = 200, message = KeyMessages.PUBLIC_KEY_INVALID)
        String encryptionPublicKey,

        @NotBlank(message = KeyMessages.PUBLIC_KEY_REQUIRED)
        @Size(max = 200, message = KeyMessages.PUBLIC_KEY_INVALID)
        String signingPublicKey,

        @NotBlank(message = KeyMessages.BUNDLE_REQUIRED)
        @Size(max = 700, message = KeyMessages.BUNDLE_TOO_LONG)
        String privateKeyBundle) {
}
