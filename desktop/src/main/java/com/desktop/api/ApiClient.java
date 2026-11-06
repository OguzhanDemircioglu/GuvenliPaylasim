package com.desktop.api;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Sunucu ile konuşan tek yer. Her cevap {@code {success, message, data, traceId}} biçimindedir; başarısızsa
 * {@link ApiException} fırlatılır. Access token 15 dakikada dolar; 401 alınırsa refresh cookie ile bir kez
 * yenilenip istek tekrarlanır.
 * <p>
 * Metotlar ağ çağrısı yaptığı için JavaFX thread'inde değil, arka planda çağrılır (ui.Async).
 */
public final class ApiClient {

    public static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final javax.net.ssl.SSLContext SSL_CONTEXT =
            PinnedTrust.context(com.desktop.config.AppConfig.get().serverPins());

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String AUTH_PATH = "/api/auth";

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    /**
     * Proxy kullanılmaz. Refresh token HttpOnly cookie olarak burada tutulur. TLS sadece 1.3 ve sunucu sertifikası
     * sabitlenmiş köklere zincirlenmeli (PinnedTrust, Security.md S1).
     */
    private final HttpClient http = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .sslContext(SSL_CONTEXT)
            .sslParameters(PinnedTrust.parameters())
            .build();
    private volatile String accessToken;
    /** Refresh token her yenilemede değişir; "Beni hatırla" kaydı bununla güncel tutulur. */
    private volatile Runnable onRefreshed = () -> {
    };

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void onRefreshed(Runnable listener) {
        this.onRefreshed = listener;
    }

    /** Şu anki refresh token (cookie'den); yoksa null. */
    public String refreshToken() {
        return cookies.getCookieStore().get(URI.create(baseUrl + AUTH_PATH)).stream()
                .filter(cookie -> REFRESH_COOKIE.equals(cookie.getName()) && !cookie.getValue().isEmpty())
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Kaydedilmiş refresh token'la oturum açar ("Beni hatırla"). Sunucu token'ı değiştirir; yenisi
     * {@link #refreshToken()} ile okunur. Token geçersizse {@link ApiException} (401) fırlatılır.
     */
    public Dto.SessionInfo resume(String refreshToken) {
        java.net.HttpCookie cookie = new java.net.HttpCookie(REFRESH_COOKIE, refreshToken);
        cookie.setPath(AUTH_PATH);
        cookie.setVersion(0);
        // CookieManager, Domain'i olmayan sunucu cookie'sine host'u (noktasızsa host + ".local") yazar; aynı değer
        // verilmezse sunucunun yenilediği cookie eskisinin yerine geçmez, ikisi birden gönderilir.
        String host = URI.create(baseUrl).getHost();
        cookie.setDomain(host.contains(".") ? host : host + ".local");
        cookies.getCookieStore().add(URI.create(baseUrl), cookie);
        Dto.SessionInfo session = read(send("POST", AUTH_PATH + "/refresh", null, false), new TypeReference<>() {
        });
        accessToken = session.accessToken();
        return session;
    }

    /** {@code phone}: {@code 5XXXXXXXXX}; {@code totpCode}: TOTP açıksa Authenticator ya da kurtarma kodu. */
    public Dto.SessionInfo login(String phone, String authSecret, String totpCode) {
        Dto.SessionInfo session = post("/api/auth/login", new Dto.LoginRequest(phone, authSecret, totpCode),
                new TypeReference<>() {
                });
        accessToken = session.accessToken();
        return session;
    }

    public void logout() {
        try {
            send("POST", "/api/auth/logout", null, false);
        } catch (ApiException ignored) {
            // Çıkışta sunucuya ulaşılamasa da yerel oturum kapanır.
        }
        accessToken = null;
    }

    public <T> T get(String path, TypeReference<T> type) {
        return read(send("GET", path, null, true), type);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        return read(send("POST", path, body, true), type);
    }

    public <T> T put(String path, Object body, TypeReference<T> type) {
        return read(send("PUT", path, body, true), type);
    }

    public void delete(String path) {
        send("DELETE", path, null, true);
    }

    private JsonNode send(String method, String path, Object body, boolean retryOnUnauthorized) {
        HttpResponse<String> response = execute(method, path, body);
        if (response.statusCode() == 401 && retryOnUnauthorized && accessToken != null && refresh()) {
            response = execute(method, path, body);
        }
        JsonNode envelope = parse(response);
        if (!envelope.path("success").asBoolean(false)) {
            throw new ApiException(response.statusCode(), envelope.path("message").asString("unexpected_error"));
        }
        return envelope.path("data");
    }

    private boolean refresh() {
        HttpResponse<String> response = execute("POST", "/api/auth/refresh", null);
        if (response.statusCode() != 200) {
            return false;
        }
        accessToken = parse(response).path("data").path("accessToken").asString();
        onRefreshed.run();
        return true;
    }

    private HttpResponse<String> execute(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json");
            if (accessToken != null && !path.startsWith("/api/auth/refresh") && !path.startsWith("/api/auth/login")) {
                request.header("Authorization", "Bearer " + accessToken);
            }
            if (body != null) {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (javax.net.ssl.SSLHandshakeException e) {
            throw new ApiException(0, Messages.SERVER_IDENTITY_FAILED);
        } catch (IOException e) {
            throw new ApiException(0, Messages.CONNECTION_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, Messages.CONNECTION_FAILED);
        }
    }

    private static JsonNode parse(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (RuntimeException e) {
            return JSON.valueToTree(Map.of("success", false, "message", "unexpected_error"));
        }
    }

    private static <T> T read(JsonNode data, TypeReference<T> type) {
        return data == null || data.isNull() || data.isMissingNode() ? null : JSON.treeToValue(data, JSON.constructType(type));
    }
}
