package com.server.modules.document.dto;

/** {@code smsNotified}: telefonu kayıtlı olup SMS bildirimi gönderilen alıcı sayısı. */
public record DocumentSentResponse(Long id, int recipientCount, int smsNotified) {
}
