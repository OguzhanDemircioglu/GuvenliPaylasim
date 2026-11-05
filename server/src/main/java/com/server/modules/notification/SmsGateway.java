package com.server.modules.notification;

/**
 * SMS servisi. Kurum SMS servisinin adresi verildiğinde bu interface'in gerçek uygulaması yazılır ve
 * {@link SimulatedSmsGateway} yerine geçer; başka bir kod değişmez.
 * <p>
 * Eski sistemden farklı olarak SMS'te hiçbir anahtar ya da parola yoktur; sadece "yeni belgeniz var" bildirimi gider.
 */
public interface SmsGateway {

    void send(String phone, String message);
}
