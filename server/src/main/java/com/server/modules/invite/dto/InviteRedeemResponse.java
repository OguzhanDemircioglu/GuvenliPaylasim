package com.server.modules.invite.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;

public record InviteRedeemResponse(Long id, DocumentKind kind, String payload, String signature, String wrapped,
                                   Instant createdAt, PartyResponse sender) {
}
