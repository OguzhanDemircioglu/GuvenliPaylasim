package com.server.modules.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Brute-force koruması: aynı IP_email ile maxAttempts yanlış denemeden sonra blockDuration boyunca login engellenir.
 */
@ConfigurationProperties("app.login-protection")
public record LoginProtectionProperties(int maxAttempts, Duration blockDuration) {
}
