package com.server.modules.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Kayıt hız sınırı: aynı IP'den maxAttempts kayıt denemesinden sonra blockDuration boyunca kayıt engellenir.
 */
@ConfigurationProperties("app.register-protection")
public record RegisterProtectionProperties(int maxAttempts, Duration blockDuration) {
}
