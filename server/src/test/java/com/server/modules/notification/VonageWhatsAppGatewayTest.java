package com.server.modules.notification;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vonage Messages API isteğinin biçimi: WhatsApp kanalı, numara başında + olmadan, Basic auth. */
class VonageWhatsAppGatewayTest {

    private static final String URL = "https://messages-sandbox.nexmo.com/v1/messages";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void requestUsesWhatsAppChannelBasicAuthAndNumberWithoutPlus() {
        VonageWhatsAppGateway gateway = new VonageWhatsAppGateway(
                new VonageProperties("key", "secret", "14157386102", URL), jsonMapper);

        HttpRequest request = gateway.buildRequest("+905551234567", "Kodunuz: 123456");

        assertThat(request.uri().toString()).isEqualTo(URL);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Authorization")).contains(
                "Basic " + Base64.getEncoder().encodeToString("key:secret".getBytes(StandardCharsets.UTF_8)));
        JsonNode body = jsonMapper.readTree(body(request));
        assertThat(body.get("channel").asString()).isEqualTo("whatsapp");
        assertThat(body.get("message_type").asString()).isEqualTo("text");
        assertThat(body.get("from").asString()).isEqualTo("14157386102");
        assertThat(body.get("to").asString()).isEqualTo("905551234567");
        assertThat(body.get("text").asString()).isEqualTo("Kodunuz: 123456");
    }

    @Test
    void missingCredentialsFailAtStartup() {
        assertThatThrownBy(() -> new VonageWhatsAppGateway(new VonageProperties("key", "", "1", URL), jsonMapper))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String body(HttpRequest request) {
        List<ByteBuffer> buffers = new ArrayList<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                buffers.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        StringBuilder text = new StringBuilder();
        buffers.forEach(buffer -> text.append(StandardCharsets.UTF_8.decode(buffer)));
        return text.toString();
    }
}
