package com.server.modules.auth;

import com.server.modules.auth.dto.ChangePasswordRequest;
import com.server.modules.auth.dto.LoginRequest;
import com.server.modules.auth.dto.PasswordResetRequest;

public interface AuthService {

    AuthResult login(LoginRequest request, ClientInfo client);

    /** Şifresiz oturum açar. Sadece DevLoginController çağırır; o da prod'da hiç oluşmaz. */
    AuthResult devLogin(String phone, ClientInfo client);

    /** Sayfa açılışında (F5 dahil) ve access token süresi dolunca çağrılır. */
    AuthResult refresh(String refreshToken);

    void logout(String refreshToken);

    /** Kullanıcının kendi şifresini değiştirmesi; diğer oturumları kapanır, mevcut oturum açık kalır. */
    void changePassword(Long userId, Long currentSessionId, ChangePasswordRequest request);

    /**
     * Parolasını unutan kullanıcı (Crypto_Design §7.5): WhatsApp kodu doğrulanır, parola ve anahtarlar yenilenir,
     * kullanıcıya gelmiş bütün kutular silinir, bütün oturumlar kapanır. Kayıtsız numara için kod hiç gönderilmediği
     * için sonuç her zaman "kod geçersiz"dir.
     */
    void resetPassword(PasswordResetRequest request);
}
