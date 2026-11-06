package com.server.modules.user;

public interface UserService {

    /**
     * Herkese açık kayıt. {@code phone} {@code 5XXXXXXXXX}, {@code password} istemcinin türettiği authSecret'tır.
     * Geçici: telefon doğrulaması (WhatsApp kodu) kodlama sırasının 2. adımında eklenir.
     */
    void register(String phone, String fullName, String password);
}
