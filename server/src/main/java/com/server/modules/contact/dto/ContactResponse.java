package com.server.modules.contact.dto;

import com.server.modules.contact.ContactStatus;

/**
 * Kişi ve güncel açık anahtarları. {@code fingerprint}/{@code signature} kayıtta saklanan doğrulanmış değerdir;
 * istemci imzayı kendi anahtarıyla doğrular ve parmak izini güncel anahtarlardan hesapladığıyla karşılaştırır.
 * Farklıysa kişinin anahtarı değişmiştir ve yeni davet kodu gerekir (Crypto_Design §7.8).
 */
public record ContactResponse(Long userId, String fullName, String phone, ContactStatus status, String fingerprint,
                              String signature, String encryptionPublicKey, String signingPublicKey) {
}
