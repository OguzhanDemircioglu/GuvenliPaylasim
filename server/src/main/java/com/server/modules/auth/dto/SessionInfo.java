package com.server.modules.auth.dto;

/**
 * Login ve refresh'in ortak cevabı. Kullanıcıya bağlı yeni bir değer gerektiğinde buraya alan eklenir ve
 * AuthServiceImpl.buildSessionInfo güncellenir. Refresh token burada yoktur, sadece HttpOnly cookie'dedir.
 */
public record SessionInfo(String accessToken, long expiresIn, SessionUser user) {
}
