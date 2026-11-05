package com.server.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * İlk admin kullanıcısının bilgileri (ADMIN_EMAIL, ADMIN_PASSWORD). Sadece V002_InitialAdmin kullanır.
 */
@ConfigurationProperties("app.admin")
public record AdminProperties(String email, String password) {
}
