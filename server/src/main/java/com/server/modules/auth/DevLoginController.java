package com.server.modules.auth;

import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.migration.versions.V001_DemoData;
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
 * Yerel geliştirmede login ekranını atlar: ilk demo kullanıcısıyla (V001_DemoData) şifresiz oturum açar.
 * Bean sadece app.dev-login.enabled=true ise oluşur (application-dev.yml); prod'da bu adres 404 döner.
 * Dev profilli bir server ağa açık olabileceği için sadece bu makineden gelen istekler kabul edilir.
 */
@RestController
@ConditionalOnProperty(name = "app.dev-login.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevLoginController {

    private final AuthService authService;
    private final RefreshCookies refreshCookies;

    @PostMapping("/api/auth/dev-login")
    public ApiResponse<SessionInfo> login(HttpServletRequest request, HttpServletResponse response) {
        if (!InetAddress.ofLiteral(request.getRemoteAddr()).isLoopbackAddress()) {
            throw new NotFoundException(GlobalMessages.URL_NOT_FOUND);
        }
        AuthResult result = authService.devLogin(V001_DemoData.DEV_LOGIN_PHONE, ClientInfo.from(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.create(result.refreshToken()).toString());
        return ApiResponse.success(result.sessionInfo());
    }
}
