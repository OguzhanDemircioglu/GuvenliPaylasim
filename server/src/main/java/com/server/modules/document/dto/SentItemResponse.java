package com.server.modules.document.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;
import java.util.List;

/** Sadece ömrü dolmamış kutular görünür; silinen kutu gönderenin listesinden de kalkar (Crypto_Design §8.4). */
public record SentItemResponse(Long id, DocumentKind kind, long sizeBytes, Instant createdAt, Instant expiresAt,
                               boolean deleteOnOpen, List<SentRecipientResponse> recipients) {
}
