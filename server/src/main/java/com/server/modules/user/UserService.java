package com.server.modules.user;

import com.server.modules.auth.dto.RegisterRequest;

public interface UserService {

    /**
     * Kaydı tamamlar (Crypto_Design §7.2): WhatsApp kodu doğrulanır, hesap ve istemcide üretilmiş anahtarlar aynı
     * transaction'da kaydedilir. Böylece "anahtarı olmayan kullanıcı" durumu oluşmaz.
     */
    void register(RegisterRequest request);
}
