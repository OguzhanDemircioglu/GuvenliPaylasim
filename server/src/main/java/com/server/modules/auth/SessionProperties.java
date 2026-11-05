package com.server.modules.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param refreshTokenTtl  oturum süresi; her yenilemede baştan başlar (kayan süre)
 * @param reuseGracePeriod birden fazla sekme aynı anda yenileme yaptığında eski token'ın kabul edildiği süre
 * @param cookieSecure     prod'da true (HTTPS), dev'de false
 */
@ConfigurationProperties("app.session")
public record SessionProperties(Duration refreshTokenTtl, Duration reuseGracePeriod, boolean cookieSecure) {
}
