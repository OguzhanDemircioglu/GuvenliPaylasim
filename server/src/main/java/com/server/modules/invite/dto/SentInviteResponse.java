package com.server.modules.invite.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;

/** Gönderenin henüz kabul edilmemiş daveti. Numaranın kayıtlı olup olmadığı burada da görünmez. */
public record SentInviteResponse(Long id, String recipientPhone, DocumentKind kind, long sizeBytes, Instant createdAt,
                                 Instant expiresAt) {
}
