package com.server.modules.invite.dto;

import java.time.Instant;

/** Numara kayıtlı olsun olmasın aynı biçimdedir (Crypto_Design §7.6). */
public record InviteCreatedResponse(Long id, Instant expiresAt) {
}
