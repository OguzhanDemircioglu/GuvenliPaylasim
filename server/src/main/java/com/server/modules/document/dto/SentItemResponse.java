package com.server.modules.document.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;
import java.util.List;

public record SentItemResponse(Long id, DocumentKind kind, long sizeBytes, Instant createdAt, Instant purgedAt,
                               List<SentRecipientResponse> recipients) {
}
