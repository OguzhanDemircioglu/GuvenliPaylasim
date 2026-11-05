package com.server.modules.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** SMS servisi bağlanana kadar mesajı sadece loglar. Masaüstü istemci bildirimi uygulama içinde gösterir. */
@Slf4j
@Component
public class SimulatedSmsGateway implements SmsGateway {

    @Override
    public void send(String phone, String message) {
        log.info("SMS (simülasyon) -> {}: {}", mask(phone), message);
    }

    /** Telefon numarası loglarda tam görünmez. */
    private static String mask(String phone) {
        return phone.length() <= 4 ? "****" : "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
