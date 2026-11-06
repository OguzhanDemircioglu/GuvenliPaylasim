package com.server.modules.auth;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.modules.auth.dto.ChangePasswordRequest;
import com.server.modules.auth.dto.LoginRequest;
import com.server.modules.auth.dto.PasswordResetRequest;
import com.server.modules.auth.dto.PhoneRequest;
import com.server.modules.auth.dto.RegisterRequest;
import com.server.modules.auth.dto.SessionInfo;
import com.server.modules.user.Phones;
import com.server.modules.user.UserService;
import com.server.modules.verification.VerificationMessages;
import com.server.modules.verification.VerificationPurpose;
import com.server.modules.verification.VerificationService;
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
 * login, kayıt, parola sıfırlama, refresh ve logout herkese açıktır; password giriş yapmış kullanıcı içindir.
 * Kayıt ve sıfırlama iki adımlıdır: start telefona WhatsApp kodu gönderir, complete kodla işlemi bitirir
 * (Crypto_Design §7.2, §7.5). start, numara kayıtlı olsun olmasın aynı cevabı verir. Kayıttan sonra istemci normal
 * girişi yapar.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final VerificationService verificationService;
    private final RefreshCookies refreshCookies;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    public ApiResponse<SessionInfo> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return respond(authService.login(request, ClientInfo.from(httpRequest)), httpResponse);
    }

    @PostMapping("/register/start")
    public ApiResponse<Void> registerStart(@Valid @RequestBody PhoneRequest request, HttpServletRequest httpRequest) {
        return startVerification(request, VerificationPurpose.REGISTER, httpRequest);
    }

    @PostMapping("/register/complete")
    public ApiResponse<Void> registerComplete(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResponse.success(GlobalMessages.RECORD_CREATED, null);
    }

    @PostMapping("/password-reset/start")
    public ApiResponse<Void> passwordResetStart(@Valid @RequestBody PhoneRequest request,
                                                HttpServletRequest httpRequest) {
        return startVerification(request, VerificationPurpose.PASSWORD_RESET, httpRequest);
    }

    @PostMapping("/password-reset/complete")
    public ApiResponse<Void> passwordResetComplete(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, null);
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

    private ApiResponse<Void> startVerification(PhoneRequest request, VerificationPurpose purpose,
                                                HttpServletRequest httpRequest) {
        verificationService.start(Phones.toE164(request.phone()), purpose, ClientInfo.from(httpRequest).ip());
        return ApiResponse.success(VerificationMessages.CODE_SENT, null);
    }

    private ApiResponse<SessionInfo> respond(AuthResult result, HttpServletResponse httpResponse) {
        if (result.refreshToken() != null) {
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.create(result.refreshToken()).toString());
        }
        return ApiResponse.success(result.sessionInfo());
    }
}
