package com.server.modules.document.dto;

import com.server.modules.document.DocumentKind;

import java.time.Instant;

/**
 * Alıcının belgeyi çözmesi için gereken her şey. {@code senderSigningPublicKey} null ise gönderenin anahtarları
 * sıfırlanmıştır ve imza doğrulanamaz; istemci bunu açıkça gösterir.
 */
public record DocumentContentResponse(Long id, DocumentKind kind, String payload, String signature, String wrappedKey,
                                      Long senderId, String senderName,
                                      String senderSigningPublicKey, Instant createdAt, Instant expiresAt,
                                      boolean deleteOnOpen) {
}
