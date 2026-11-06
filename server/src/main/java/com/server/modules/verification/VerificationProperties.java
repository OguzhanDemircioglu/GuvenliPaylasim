package com.server.modules.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Doğrulama kodu kuralları (Crypto_Design §7.9, Security.md S8).
 *
 * @param codeTtl        kodun geçerlilik süresi
 * @param maxAttempts    bir kod için en fazla yanlış deneme; aşılınca kod silinir
 * @param perPhoneHourly numara başına saatte en fazla kod isteği
 * @param perIpHourly    IP başına saatte en fazla kod isteği
 * @param dailyLimit     sistem geneli günlük tavan; aşılınca kod gönderimi durur ve hata loglanır
 */
@ConfigurationProperties("app.verification")
public record VerificationProperties(Duration codeTtl, int maxAttempts, int perPhoneHourly, int perIpHourly,
                                     int dailyLimit) {
}
