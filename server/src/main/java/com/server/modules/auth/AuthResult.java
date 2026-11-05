package com.server.modules.auth;

import com.server.modules.auth.dto.SessionInfo;

/**
 * @param refreshToken yeni refresh token; null ise cookie değiştirilmez (30 saniyelik pencere)
 */
public record AuthResult(SessionInfo sessionInfo, String refreshToken) {
}
