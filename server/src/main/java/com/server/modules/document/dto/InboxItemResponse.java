package com.server.modules.document.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;

public record InboxItemResponse(Long id, DocumentKind kind, long sizeBytes, Long senderId, String senderName,
                                Instant createdAt, Instant readAt) {
}
