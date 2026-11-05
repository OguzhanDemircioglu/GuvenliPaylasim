package com.server.modules.document.dto;

import java.time.Instant;

public record SentRecipientResponse(Long documentId, Long userId, String fullName, Long unitId,
                                    Instant readAt, Instant deletedAt) {
}
