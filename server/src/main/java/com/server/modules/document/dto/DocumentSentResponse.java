package com.server.modules.document.dto;

/** v2'de kişilere SMS/WhatsApp bildirimi gitmez (Crypto_Design §7.7); bildirim uygulama içindedir. */
public record DocumentSentResponse(Long id, int recipientCount) {
}
