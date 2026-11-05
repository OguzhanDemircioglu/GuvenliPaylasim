package com.server.modules.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh token cookie'si: HttpOnly, SameSite=Strict, sadece /api/auth yoluna gönderilir.
 */
@Component
@RequiredArgsConstructor
public class RefreshCookies {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/api/auth";

    private final SessionProperties properties;

    public ResponseCookie create(String refreshToken) {
        return build(refreshToken, properties.refreshTokenTtl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
