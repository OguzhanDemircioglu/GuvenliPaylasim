package com.server.acceptance;

import com.jayway.jsonpath.JsonPath;
import com.server.modules.contact.ContactService;
import com.server.modules.key.Fingerprints;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Güvenlik kabul testleri (Security.md §5): sunucu bütünüyle ayağa kalkar, gerçek PostgreSQL 18'e (gömülü, Docker'sız)
 * bağlanır ve istekler HTTP katmanından geçer. Her test kendi kullanıcılarını yeni numaralarla oluşturur; testler
 * aynı veritabanını paylaşır. Kayıt kodu test modunda cevapta döner (TEST_MODE).
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AcceptanceTest {

    private static final EmbeddedPostgres POSTGRES;
    private static final Path KEYS;
    private static final AtomicInteger NUMBERS = new AtomicInteger();
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Her çalıştırmada farklı numara bloğu: aynı JVM'de art arda çalışmalar çakışmaz. */
    private static final int BLOCK = RANDOM.nextInt(90) + 10;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
            KEYS = Files.createTempDirectory("acceptance-jwt");
            KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
            rsa.initialize(2048);
            KeyPair pair = rsa.generateKeyPair();
            Files.writeString(KEYS.resolve("private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            Files.writeString(KEYS.resolve("public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.hikari.schema", () -> "acceptance");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "acceptance");
        registry.add("app.jwt.key-id", () -> "acceptance");
        registry.add("app.jwt.private-key-path", () -> KEYS.resolve("private.pem").toString());
        registry.add("app.jwt.public-key-path", () -> KEYS.resolve("public.pem").toString());
        registry.add("app.security.seal-key", () -> Base64.getEncoder().encodeToString(randomBytes(32)));
        registry.add("app.verification.test-mode", () -> "true");
        registry.add("app.messaging.provider", () -> "simulated");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    private ContactService contactService;

    /** Kayıtlı ve giriş yapmış bir kullanıcı. {@code token}: erişim jetonu; {@code refreshCookie}: Set-Cookie değeri. */
    protected record Account(Long id, String phone, String password, String token, String refreshCookie,
                             String encryptionPublicKey, String signingPublicKey) {
        String fingerprint() {
            return Fingerprints.of(encryptionPublicKey, signingPublicKey);
        }
    }

    /** Yeni numara, gerçek X25519/Ed25519 açık anahtarlar; kayıt ve giriş HTTP üzerinden. */
    protected Account register() throws Exception {
        String phone = freshPhone();
        String ip = randomIp();
        MvcResult started = call("POST", "/api/auth/register/start", "{\"phone\":\"" + phone + "\"}", null, ip);
        String code = JsonPath.read(body(started), "$.data.testCode");
        String encryption = publicKey("X25519");
        String signing = publicKey("Ed25519");
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        String register = """
                {"phone":"%s","code":"%s","fullName":"Test Kullanıcı","password":"%s",
                 "keys":{"encryptionPublicKey":"%s","signingPublicKey":"%s","privateKeyBundle":"bundle"}}"""
                .formatted(phone, code, password, encryption, signing);
        expect(call("POST", "/api/auth/register/complete", register, null, ip), 200);
        MvcResult login = login(phone, password, ip);
        expect(login, 200);
        return new Account(((Number) JsonPath.read(body(login), "$.data.user.id")).longValue(),
                JsonPath.read(body(login), "$.data.user.phone"), password,
                JsonPath.read(body(login), "$.data.accessToken"),
                login.getResponse().getHeader(HttpHeaders.SET_COOKIE), encryption, signing);
    }

    protected MvcResult login(String phone, String password, String ip) throws Exception {
        return call("POST", "/api/auth/login",
                "{\"phone\":\"%s\",\"password\":\"%s\"}".formatted(phone, password), null, ip);
    }

    /** İki yönlü doğrulanmış kişi (davet akışının sonucu); belge göndermek için gerekir. */
    protected void connect(Account a, Account b) {
        contactService.saveVerified(a.id(), b.id(), b.fingerprint(), "sig");
        contactService.saveVerified(b.id(), a.id(), a.fingerprint(), "sig");
    }

    /** {@code from} → {@code to}; dönen belge numarası. */
    protected long sendDocument(Account from, Account... to) throws Exception {
        StringBuilder recipients = new StringBuilder();
        for (Account r : to) {
            recipients.append(recipients.isEmpty() ? "" : ",")
                    .append("{\"userId\":").append(r.id()).append(",\"wrappedKey\":\"wk\"}");
        }
        MvcResult sent = call("POST", "/api/documents", """
                {"kind":"TEXT","payload":"%s","signature":"sig","lifetimeHours":24,"deleteOnOpen":false,
                 "recipients":[%s]}""".formatted(Base64.getEncoder().encodeToString("gizli".getBytes()), recipients),
                from.token(), randomIp());
        expect(sent, 200);
        return ((Number) JsonPath.read(body(sent), "$.data.id")).longValue();
    }

    protected MvcResult call(String method, String path, String json, String token, String ip) throws Exception {
        MockHttpServletRequestBuilder request = request(org.springframework.http.HttpMethod.valueOf(method), path)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request).andReturn();
    }

    protected MvcResult call(String method, String path, String json, Account as) throws Exception {
        return call(method, path, json, as == null ? null : as.token(), randomIp());
    }

    protected static String body(MvcResult result) throws IOException {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static void expect(MvcResult result, int status) throws IOException {
        if (result.getResponse().getStatus() != status) {
            throw new AssertionError("Beklenen HTTP " + status + ", gelen " + result.getResponse().getStatus() + ": "
                    + body(result));
        }
    }

    /** Daha önce kullanılmamış numara, istekte yazıldığı biçimde ({@code 5XXXXXXXXX}). */
    protected static String freshPhone() {
        return "5" + BLOCK + String.format("%07d", NUMBERS.incrementAndGet());
    }

    protected static String randomIp() {
        return "10." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256) + "." + (RANDOM.nextInt(254) + 1);
    }

    protected static String randomHex(int bytes) {
        return HexFormat.of().formatHex(randomBytes(bytes));
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** Gerçek X25519 ve Ed25519 açık anahtarlarıyla geçerli bir anahtar paketi (JSON). */
    protected static String validKeysJson() throws java.security.GeneralSecurityException {
        return "{\"encryptionPublicKey\":\"%s\",\"signingPublicKey\":\"%s\",\"privateKeyBundle\":\"b\"}"
                .formatted(publicKey("X25519"), publicKey("Ed25519"));
    }

    private static String publicKey(String algorithm) throws java.security.GeneralSecurityException {
        return Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance(algorithm).generateKeyPair().getPublic().getEncoded());
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
