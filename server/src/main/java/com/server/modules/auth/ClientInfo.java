package com.server.modules.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * İsteği yapan istemcinin bilgileri. IP her zaman getRemoteAddr() ile okunur; proxy arkasında gerçek IP'yi
 * server.forward-headers-strategy=native (FORWARD_HEADERS_STRATEGY) sağlar.
 */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}
