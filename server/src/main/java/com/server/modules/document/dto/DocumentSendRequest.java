package com.server.modules.document.dto;

import com.server.modules.document.DocumentKind;
import com.server.modules.document.DocumentMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * İstemcide şifrelenip imzalanmış belge. {@code payload} Base64'tür; 14.000.000 karakter yaklaşık 10 MB eder
 * (Jackson'ın varsayılan 20 milyon karakterlik metin sınırının altında kalır).
 */
public record DocumentSendRequest(
        @NotNull(message = DocumentMessages.KIND_REQUIRED)
        DocumentKind kind,

        @NotBlank(message = DocumentMessages.PAYLOAD_REQUIRED)
        @Size(max = 14_000_000, message = DocumentMessages.PAYLOAD_TOO_LARGE)
        String payload,

        @NotBlank(message = DocumentMessages.SIGNATURE_REQUIRED)
        @Size(max = 200, message = DocumentMessages.SIGNATURE_TOO_LONG)
        String signature,

        @NotEmpty(message = DocumentMessages.RECIPIENTS_REQUIRED)
        @Size(max = 2000, message = DocumentMessages.RECIPIENTS_TOO_MANY)
        List<@Valid @NotNull(message = DocumentMessages.RECIPIENT_REQUIRED) RecipientKeyRequest> recipients,

        /** 1, 24, 168 ya da 720 (BoxLifetimes). */
        @NotNull(message = DocumentMessages.LIFETIME_INVALID)
        Integer lifetimeHours,

        boolean deleteOnOpen) {
}
