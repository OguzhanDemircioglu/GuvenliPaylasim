package com.server.modules.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vonage Messages API erişim bilgileri. Sadece ortam değişkeninden gelir, repoya yazılmaz.
 *
 * @param apiKey    VONAGE_API_KEY
 * @param apiSecret VONAGE_API_SECRET
 * @param channel   VONAGE_CHANNEL: {@code sms} (varsayılan) ya da {@code whatsapp}
 * @param from      VONAGE_FROM: SMS'te gönderen adı (en fazla 11 harf/rakam, ör. GuvenliPay) ya da numara;
 *                  WhatsApp'ta gönderen WhatsApp numarası (sandbox'ta Vonage'in verdiği numara)
 * @param url       VONAGE_MESSAGES_URL: varsayılan https://api.nexmo.com/v1/messages; WhatsApp sandbox için
 *                  https://messages-sandbox.nexmo.com/v1/messages
 */
@ConfigurationProperties("app.messaging.vonage")
public record VonageProperties(String apiKey, String apiSecret, String channel, String from, String url) {
}
