package com.server.modules.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Brute-force koruması: aynı IP_telefon ile maxAttempts yanlış denemeden sonra blockDuration boyunca login engellenir.
 * IP değiştirerek aynı numarayı denemeye karşı numara başına toplam sınır da vardır ({@code phoneMaxAttempts}, bütün
 * IP'lerden; Security.md S4). Bu sınır yüksek tutulur: düşük olursa başkası bir numarayı kolayca kilitleyebilirdi.
 */
@ConfigurationProperties("app.login-protection")
public record LoginProtectionProperties(int maxAttempts, Duration blockDuration, int phoneMaxAttempts) {
}
