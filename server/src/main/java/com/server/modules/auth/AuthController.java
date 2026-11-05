package com.server.modules.auth;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.auth.dto.ChangePasswordRequest;
import com.server.modules.auth.dto.LoginRequest;
import com.server.modules.auth.dto.RegisterRequest;
import com.server.modules.auth.dto.SessionInfo;
import com.server.modules.user.UserService;
import com.server.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * login, register, refresh ve logout herkese açıktır; password giriş yapmış kullanıcı içindir.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RegisterRateLimiter registerRateLimiter;
    private final RefreshCookies refreshCookies;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    public ApiResponse<SessionInfo> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return respond(authService.login(request, ClientInfo.from(httpRequest)), httpResponse);
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        registerRateLimiter.acquire(ClientInfo.from(httpRequest).ip());
        userService.register(request.email(), request.fullName(), request.password());
        return ApiResponse.success(GlobalMessages.RECORD_CREATED, null);
    }

    @PostMapping("/refresh")
    public ApiResponse<SessionInfo> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
                                            HttpServletResponse httpResponse) {
        return respond(authService.refresh(refreshToken), httpResponse);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
                                    HttpServletResponse httpResponse) {
        authService.logout(refreshToken);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString());
        return ApiResponse.success(null);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.id(), currentUser.sessionId(), request);
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, null);
    }

    private ApiResponse<SessionInfo> respond(AuthResult result, HttpServletResponse httpResponse) {
        if (result.refreshToken() != null) {
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.create(result.refreshToken()).toString());
        }
        return ApiResponse.success(result.sessionInfo());
    }
}
