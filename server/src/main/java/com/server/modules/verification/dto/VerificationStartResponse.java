package com.server.modules.verification.dto;

/**
 * Kod isteğinin cevabı. {@code testCode} sadece sunucu test modundayken ve kayıtta doludur (VerificationProperties);
 * normalde null'dır.
 */
public record VerificationStartResponse(String testCode) {
}
