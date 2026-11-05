package com.server;

import com.server.common.message.GlobalMessages;
import com.server.common.web.TraceIdFilter;
import com.server.support.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security firewall'unun reddettiği istek, gerçek servlet container'da /error'a düşer ve ApiResponse formatında 400 döner.
 * MockMvc error dispatch yapmadığı için gerçek port ile çalışır. Docker yoksa otomatik olarak atlanır.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class FirewallIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("DB_SCHEMA", () -> "starterapp");
        registry.add("app.admin.email", () -> "admin@test.local");
        registry.add("app.admin.password", () -> "Admin12345");
        TestJwtKeys.register(registry);
    }

    @LocalServerPort
    private int port;

    @Test
    void rejectedRequestReturns400InApiFormat(CapturedOutput output) throws Exception {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            // StrictHttpFirewall URL'de ';' kabul etmez.
            URI uri = URI.create("http://localhost:" + port + "/api/categories;x=y");
            response = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = JsonMapper.builder().build().readTree(response.body());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asString()).isEqualTo(GlobalMessages.INVALID_REQUEST);
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("traceId").asString())
                .isNotBlank()
                .isEqualTo(response.headers().firstValue(TraceIdFilter.HEADER).orElseThrow());
        assertThat(output).contains("400 " + GlobalMessages.INVALID_REQUEST + " GET /api/categories;x=y");
    }
}
