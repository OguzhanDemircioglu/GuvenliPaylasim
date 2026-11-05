package com.server.modules.key.dto;

/** Belge gönderilebilecek bir kişi ve açık anahtarları. */
public record RecipientResponse(Long userId, String fullName, Long unitId,
                                String encryptionPublicKey, String signingPublicKey) {
}
