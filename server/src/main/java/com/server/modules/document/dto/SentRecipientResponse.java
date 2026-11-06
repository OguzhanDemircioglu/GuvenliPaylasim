package com.server.modules.document.dto;

import java.time.Instant;

/** Alıcı kutuyu silince satırı da silinir; burada sadece hâlâ kutusu olan alıcılar görünür. */
public record SentRecipientResponse(Long documentId, Long userId, String fullName, Instant readAt) {
}
