package com.server.modules.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vonage Messages API (WhatsApp) erişim bilgileri. Sadece ortam değişkeninden gelir, repoya yazılmaz.
 *
 * @param apiKey    VONAGE_API_KEY
 * @param apiSecret VONAGE_API_SECRET
 * @param from      VONAGE_WHATSAPP_FROM: gönderen WhatsApp numarası (sandbox'ta Vonage'in verdiği numara)
 * @param url       VONAGE_MESSAGES_URL: sandbox için https://messages-sandbox.nexmo.com/v1/messages,
 *                  gerçek hesap için https://api.nexmo.com/v1/messages
 */
@ConfigurationProperties("app.messaging.vonage")
public record VonageProperties(String apiKey, String apiSecret, String from, String url) {
}
