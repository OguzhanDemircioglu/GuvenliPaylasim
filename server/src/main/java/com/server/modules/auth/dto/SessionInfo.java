package com.server.modules.auth.dto;

import com.server.modules.menu.MenuNode;

import java.util.List;

/**
 * Login ve refresh'in ortak cevabı. Frontend her iki durumda da store'u bu objeyle doldurur.
 * Kullanıcıya bağlı yeni bir değer gerektiğinde buraya alan eklenir ve AuthServiceImpl.buildSessionInfo güncellenir.
 * Refresh token burada yoktur, sadece HttpOnly cookie'dedir.
 */
public record SessionInfo(String accessToken, long expiresIn, SessionUser user, List<MenuNode> menus) {
}
