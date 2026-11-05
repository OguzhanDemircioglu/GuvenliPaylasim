package com.server.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * İsteği yapan kullanıcının bilgileri. Her istekte access token'ın içinden okunur, DB'ye gidilmez.
 * Server session kullanılmaz; uygulama stateless çalışır.
 */
@Component
public class CurrentUser {

    public Long id() {
        return Long.valueOf(jwt().getSubject());
    }

    public String email() {
        return jwt().getClaimAsString(JwtClaims.EMAIL);
    }

    public Role role() {
        return Role.valueOf(jwt().getClaimAsString(JwtClaims.ROLE));
    }

    public Long sessionId() {
        Number sessionId = jwt().getClaim(JwtClaims.SESSION_ID);
        return sessionId.longValue();
    }

    /** Giriş yapılmamışsa boş döner (ör. audit alanları için sistem işlemleri). */
    public Optional<Long> findId() {
        return findJwt().map(jwt -> Long.valueOf(jwt.getSubject()));
    }

    private Jwt jwt() {
        return findJwt().orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
    }

    private static Optional<Jwt> findJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token ? Optional.of(token.getToken()) : Optional.empty();
    }
}
