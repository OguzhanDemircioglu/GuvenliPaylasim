package com.server.modules.notification;

/**
 * Doğrulama mesajı servisi (kayıt ve parola sıfırlama kodu, Crypto_Design §7.2). Uygulamalar:
 * {@link VonageMessageGateway} (SMS ya da WhatsApp) ve {@link SimulatedSmsGateway} (sadece log). Seçim app.messaging.provider ile
 * yapılır. Mesajda anahtar ya da parola yoktur; sadece 6 haneli doğrulama kodu gider.
 */
public interface SmsGateway {

    void send(String phone, String message);
}
