package com.server.modules.document.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;

/** {@code expiresAt}: kutunun silineceği an. {@code deleteOnOpen}: açılınca sunucudan silinir (Crypto_Design §8). */
public record InboxItemResponse(Long id, DocumentKind kind, long sizeBytes, Long senderId, String senderName,
                                Instant createdAt, Instant expiresAt, boolean deleteOnOpen, Instant readAt) {
}
