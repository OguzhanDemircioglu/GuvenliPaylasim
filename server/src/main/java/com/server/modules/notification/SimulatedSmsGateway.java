package com.server.modules.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mesaj servisi bağlı değilken (app.messaging.provider=simulated, varsayılan) mesajı sadece loglar. Doğrulama kodu
 * log'da görünür: dev'de ve Vonage bilgileri girilmemiş demo sunucusunda kayıt böyle tamamlanır. Gerçek kullanıcı
 * verisiyle bu mod kullanılmaz.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedSmsGateway implements SmsGateway {

    @Override
    public void send(String phone, String message) {
        log.info("Mesaj (simülasyon) -> {}: {}", mask(phone), message);
    }

    /** Telefon numarası loglarda tam görünmez. */
    private static String mask(String phone) {
        return phone.length() <= 4 ? "****" : "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
