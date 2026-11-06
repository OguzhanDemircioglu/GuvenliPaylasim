package com.server.modules.totp.dto;

/** {@code secret}: Base32 anahtar (elle girmek için); {@code uri}: otpauth adresi (QR kodun içeriği). */
public record TotpSetupResponse(String secret, String uri) {
}
