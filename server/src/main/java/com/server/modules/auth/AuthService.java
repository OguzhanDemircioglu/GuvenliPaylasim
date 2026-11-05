package com.server.modules.auth;

import com.server.modules.auth.dto.ChangePasswordRequest;
import com.server.modules.auth.dto.LoginRequest;

public interface AuthService {

    AuthResult login(LoginRequest request, ClientInfo client);

    /** Şifresiz oturum açar. Sadece DevLoginController çağırır; o da prod'da hiç oluşmaz. */
    AuthResult devLogin(String email, ClientInfo client);

    /** Sayfa açılışında (F5 dahil) ve access token süresi dolunca çağrılır. */
    AuthResult refresh(String refreshToken);

    void logout(String refreshToken);

    /** Kullanıcının kendi şifresini değiştirmesi; diğer oturumları kapanır, mevcut oturum açık kalır. */
    void changePassword(Long userId, Long currentSessionId, ChangePasswordRequest request);
}
