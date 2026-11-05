package com.server.modules.auth;

import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.migration.AdminProperties;
import com.server.modules.auth.dto.SessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;

/**
 * Yerel geliştirmede login ekranını atlar: ADMIN_EMAIL kullanıcısıyla şifresiz oturum açar.
 * Bean sadece app.dev-login.enabled=true ise oluşur (application-dev.yml); prod'da bu adres 404 döner.
 * Dev profilli bir server ağa açık olabileceği için sadece bu makineden gelen istekler kabul edilir.
 */
@RestController
@ConditionalOnProperty(name = "app.dev-login.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevLoginController {

    private final AuthService authService;
    private final AdminProperties admin;
    private final RefreshCookies refreshCookies;

    @PostMapping("/api/auth/dev-login")
    public ApiResponse<SessionInfo> login(HttpServletRequest request, HttpServletResponse response) {
        if (!InetAddress.ofLiteral(request.getRemoteAddr()).isLoopbackAddress()) {
            throw new NotFoundException(GlobalMessages.URL_NOT_FOUND);
        }
        AuthResult result = authService.devLogin(admin.email(), ClientInfo.from(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.create(result.refreshToken()).toString());
        return ApiResponse.success(result.sessionInfo());
    }
}
