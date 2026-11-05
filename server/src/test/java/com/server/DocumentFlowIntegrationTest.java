package com.server;

import com.jayway.jsonpath.JsonPath;
import com.server.modules.document.DocumentMessages;
import com.server.modules.key.KeyMessages;
import com.server.modules.user.UserMessages;
import com.server.security.AuthSecrets;
import com.server.support.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anahtar dizini ve belge akışı uçtan uca (bellekte H2). Sunucu şifreli veriyi opak taşır; şifreleme istemcinin
 * işidir, bu yüzden burada payload rastgele bayttır.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String PASSWORD = "User12345";
    private static final String ADMIN_SECRET = AuthSecrets.derive(ADMIN_EMAIL, PASSWORD);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:documentflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS starterapp");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("DB_SCHEMA", () -> "starterapp");
        registry.add("app.admin.email", () -> ADMIN_EMAIL);
        registry.add("app.admin.password", () -> PASSWORD);
        registry.add("app.session.cookie-secure", () -> "false");
        TestJwtKeys.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void documentTravelsEncryptedAndIsPurgedWhenEveryRecipientDeletesIt() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_SECRET);
        long ankara = createUnit(admin, "Ankara " + UUID.randomUUID());
        long izmir = createUnit(admin, "İzmir " + UUID.randomUUID());

        String sender = loginNewUserWithKeys(admin, ankara);
        String alice = loginNewUserWithKeys(admin, izmir);
        String bob = loginNewUserWithKeys(admin, izmir);
        createUser(admin, izmir, "05551112233"); // anahtarı yok: alıcı listesinde çıkmamalı

        mockMvc.perform(withToken(get("/api/keys/recipients").param("unitIds", ankara + "," + izmir), sender))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
        long aliceId = currentUserId(alice);
        long bobId = currentUserId(bob);

        String payload = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
        String sent = mockMvc.perform(withToken(post("/api/documents"), sender)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody(payload, aliceId, bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientCount").value(2))
                .andReturn().getResponse().getContentAsString();
        long documentId = ((Number) JsonPath.read(sent, "$.data.id")).longValue();

        mockMvc.perform(withToken(get("/api/documents/inbox"), alice))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].readAt").isEmpty());

        mockMvc.perform(withToken(get("/api/documents/" + documentId), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payload").value(payload))
                .andExpect(jsonPath("$.data.wrappedKey").value("wrapped-for-" + aliceId))
                .andExpect(jsonPath("$.data.senderSigningPublicKey").isNotEmpty());

        // Alıcı olmayan biri belgeyi göremez.
        mockMvc.perform(withToken(get("/api/documents/" + documentId), sender)).andExpect(status().isNotFound());

        mockMvc.perform(withToken(delete("/api/documents/" + documentId), alice)).andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/documents/inbox"), alice)).andExpect(jsonPath("$.data", hasSize(0)));
        assertThat(jdbc.queryForObject("SELECT payload IS NULL FROM documents WHERE id = ?", Boolean.class, documentId))
                .as("bir alıcı daha silmedi; içerik durmalı").isFalse();

        mockMvc.perform(withToken(delete("/api/documents/" + documentId), bob)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT payload IS NULL FROM documents WHERE id = ?", Boolean.class, documentId))
                .as("son alıcı da sildi; şifreli içerik kalıcı olarak silinmeli").isTrue();

        mockMvc.perform(withToken(get("/api/documents/sent"), sender))
                .andExpect(jsonPath("$.data[0].purgedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].recipients", hasSize(2)))
                .andExpect(jsonPath("$.data[0].recipients[0].readAt").isNotEmpty());
    }

    @Test
    void recipientsMustHaveKeysAndSenderCannotSendToSelf() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_SECRET);
        long unit = createUnit(admin, "Birim " + UUID.randomUUID());
        String sender = loginNewUserWithKeys(admin, unit);
        long senderId = currentUserId(sender);
        long withoutKeys = userId(createUser(admin, unit, null));

        String payload = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        expectError(withToken(post("/api/documents"), sender).contentType(MediaType.APPLICATION_JSON)
                .content(sendBody(payload, withoutKeys)), 422, DocumentMessages.RECIPIENT_INVALID);
        expectError(withToken(post("/api/documents"), sender).contentType(MediaType.APPLICATION_JSON)
                .content(sendBody(payload, senderId)), 422, DocumentMessages.RECIPIENT_INVALID);
    }

    @Test
    void keysAreCreatedOnceAndRejectGarbage() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_SECRET);
        long unit = createUnit(admin, "Birim " + UUID.randomUUID());
        String email = createUser(admin, unit, null);
        String user = token(email, PASSWORD);

        expectError(withToken(post("/api/keys/me"), user).contentType(MediaType.APPLICATION_JSON)
                .content(keyBody("bm90LWEta2V5", "bm90LWEta2V5")), 422, KeyMessages.PUBLIC_KEY_INVALID);

        mockMvc.perform(withToken(post("/api/keys/me"), user).contentType(MediaType.APPLICATION_JSON)
                .content(keyBody(publicKey("X25519"), publicKey("Ed25519")))).andExpect(status().isOk());
        expectError(withToken(post("/api/keys/me"), user).contentType(MediaType.APPLICATION_JSON)
                .content(keyBody(publicKey("X25519"), publicKey("Ed25519"))), 422, KeyMessages.ALREADY_EXISTS);
    }

    @Test
    void adminPasswordResetRemovesKeysAndForcesPasswordChange() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_SECRET);
        long unit = createUnit(admin, "Birim " + UUID.randomUUID());
        String email = createUser(admin, unit, null);
        String user = token(email, PASSWORD);
        mockMvc.perform(withToken(post("/api/keys/me"), user).contentType(MediaType.APPLICATION_JSON)
                .content(keyBody(publicKey("X25519"), publicKey("Ed25519")))).andExpect(status().isOk());

        mockMvc.perform(withToken(put("/api/users/" + userId(email) + "/password"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Other12345\"}"))
                .andExpect(status().isOk());

        String relogin = token(email, "Other12345");
        mockMvc.perform(withToken(get("/api/keys/me"), relogin)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE email = ?", Boolean.class, email))
                .isTrue();
    }

    @Test
    void userEmailCannotChangeAndUserRoleNeedsUnit() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_SECRET);
        expectError(withToken(post("/api/users"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"u-" + UUID.randomUUID() + "@test.local\",\"fullName\":\"U\",\"password\":\""
                        + PASSWORD + "\",\"role\":\"USER\"}"), 422, UserMessages.UNIT_REQUIRED);

        long unit = createUnit(admin, "Birim " + UUID.randomUUID());
        long id = userId(createUser(admin, unit, null));
        expectError(withToken(put("/api/users/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other@test.local\",\"fullName\":\"U\",\"role\":\"USER\",\"active\":true,"
                        + "\"unitId\":" + unit + ",\"version\":0}"), 422, UserMessages.EMAIL_IMMUTABLE);
    }

    private String loginNewUserWithKeys(String adminToken, long unitId) throws Exception {
        String user = token(createUser(adminToken, unitId, null), PASSWORD);
        mockMvc.perform(withToken(post("/api/keys/me"), user).contentType(MediaType.APPLICATION_JSON)
                .content(keyBody(publicKey("X25519"), publicKey("Ed25519")))).andExpect(status().isOk());
        return user;
    }

    private static String sendBody(String payload, long... recipientIds) {
        StringBuilder recipients = new StringBuilder();
        for (long id : recipientIds) {
            if (!recipients.isEmpty()) {
                recipients.append(',');
            }
            recipients.append("{\"userId\":").append(id).append(",\"wrappedKey\":\"wrapped-for-").append(id).append("\"}");
        }
        return "{\"kind\":\"TEXT\",\"payload\":\"" + payload + "\",\"signature\":\"c2ln\",\"recipients\":[" + recipients + "]}";
    }

    private static String keyBody(String encryptionKey, String signingKey) {
        return "{\"encryptionPublicKey\":\"" + encryptionKey + "\",\"signingPublicKey\":\"" + signingKey
                + "\",\"privateKeyBundle\":\"opaque\"}";
    }

    private static String publicKey(String algorithm) throws Exception {
        return Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance(algorithm).generateKeyPair().getPublic().getEncoded());
    }

    private long createUnit(String adminToken, String name) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/units"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private String createUser(String adminToken, long unitId, String phone) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        mockMvc.perform(withToken(post("/api/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Test " + email.substring(5, 13)
                                + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"USER\",\"unitId\":" + unitId
                                + (phone == null ? "" : ",\"phone\":\"" + phone + "\"") + "}"))
                .andExpect(status().isOk());
        return email;
    }

    private String token(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    /** Access token'ın {@code sub} claim'i kullanıcı id'sidir. */
    private static long currentUserId(String token) {
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return Long.parseLong(JsonPath.read(claims, "$.sub"));
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ? AND deleted = false", Long.class, email);
    }

    private void expectError(MockHttpServletRequestBuilder request, int status, String message) throws Exception {
        ResultActions result = mockMvc.perform(request);
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
