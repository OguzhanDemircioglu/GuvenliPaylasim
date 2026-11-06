package com.server.modules.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Doğrulama kodunu Vonage Messages API ile (Basic auth) SMS ya da WhatsApp olarak gönderir (Crypto_Design §7.2).
 * Sadece app.messaging.provider=vonage (MESSAGING_PROVIDER) iken oluşur; yoksa SimulatedSmsGateway kullanılır.
 * <p>
 * SMS: deneme hesabında sadece Vonage panelinde doğrulanmış numaralara gider; Türkiye'de harfli gönderen adı BTK
 * kaydı ister, kayıtsız ad operatörde değiştirilebilir ya da engellenebilir.
 * WhatsApp: sandbox'ta alıcı önce sandbox numarasına katılma mesajı atmalıdır; gerçek hesapta Meta onaylı şablon gerekir.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "vonage")
public class VonageMessageGateway implements SmsGateway {

    private static final Set<String> CHANNELS = Set.of("sms", "whatsapp");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final VonageProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public VonageMessageGateway(VonageProperties properties, JsonMapper jsonMapper) {
        if (isBlank(properties.apiKey()) || isBlank(properties.apiSecret()) || isBlank(properties.from())
                || isBlank(properties.url())) {
            throw new IllegalStateException("Vonage messaging is enabled but VONAGE_API_KEY, VONAGE_API_SECRET, "
                    + "VONAGE_FROM or VONAGE_MESSAGES_URL is missing");
        }
        if (!CHANNELS.contains(properties.channel())) {
            throw new IllegalStateException("VONAGE_CHANNEL must be sms or whatsapp");
        }
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public void send(String phone, String message) {
        HttpRequest request = buildRequest(phone, message);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Gövde hata ayrıntısı içerir; numara ya da mesaj içermez.
                throw new IllegalStateException("Vonage returned " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Vonage request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Vonage request interrupted", e);
        }
    }

    /** Paket içi: testler gövdeyi ve başlıkları doğrular. */
    HttpRequest buildRequest(String phone, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("from", properties.from());
        body.put("to", phone.startsWith("+") ? phone.substring(1) : phone);
        body.put("message_type", "text");
        body.put("text", message);
        body.put("channel", properties.channel());

        String credentials = properties.apiKey() + ":" + properties.apiSecret();
        return HttpRequest.newBuilder(URI.create(properties.url()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
