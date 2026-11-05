package com.server;

import com.jayway.jsonpath.JsonPath;
import com.server.common.message.GlobalMessages;
import com.server.modules.auth.AuthMessages;
import com.server.modules.auth.RefreshCookies;
import com.server.modules.unit.UnitMessages;
import com.server.security.AuthSecrets;
import com.server.support.TestJwtKeys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gerçek PostgreSQL 18 üzerinde uçtan uca akışlar. Docker yoksa otomatik olarak atlanır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin12345";
    private static final String ADMIN_SECRET = AuthSecrets.derive(ADMIN_EMAIL, ADMIN_PASSWORD);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("DB_SCHEMA", () -> "starterapp");
        registry.add("app.admin.email", () -> ADMIN_EMAIL);
        registry.add("app.admin.password", () -> ADMIN_PASSWORD);
        registry.add("app.session.reuse-grace-period", () -> "2s");
        registry.add("app.session.cookie-secure", () -> "false");
        TestJwtKeys.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsAreAppliedOnce() {
        List<Integer> versions = jdbc.queryForList("SELECT version FROM data_migration_history ORDER BY version", Integer.class);
        assertThat(versions).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void loginReturnsSessionInfoAndRefreshCookie() throws Exception {
        MockHttpServletResponse response = login(ADMIN_EMAIL.toUpperCase(), ADMIN_SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.user.role").value("SUPERADMIN"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.menus[*].titleKey", hasItem("menu_administration")))
                .andExpect(jsonPath("$.data.menus[0].titleKey").value("menu_home"))
                .andExpect(jsonPath("$.data.menus[0].route").value("/"))
                .andExpect(jsonPath("$.data.menus[1].titleKey").value("menu_definitions"))
                .andExpect(jsonPath("$.data.menus[1].route").value("/definitions"))
                .andExpect(jsonPath("$.data.menus[1].icon").value("setting"))
                .andExpect(jsonPath("$.data.menus[1].children[0].titleKey").value("menu_units"))
                .andExpect(jsonPath("$.data.menus[1].children[0].route").value("/definitions/units"))
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
        assertThat(response.getCookie(RefreshCookies.NAME)).isNotNull();
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        expectError(login(ADMIN_EMAIL, "wrong-password"), 401, AuthMessages.INVALID_CREDENTIALS);
    }

    @Test
    void refreshRotatesTokenToleratesConcurrentReuseAndDetectsTheft() throws Exception {
        String first = refreshCookie(login(ADMIN_EMAIL, ADMIN_SECRET).andReturn().getResponse());

        MockHttpServletResponse rotated = refresh(first).andExpect(status().isOk()).andReturn().getResponse();
        String second = refreshCookie(rotated);
        assertThat(second).isNotEqualTo(first);

        // İkinci bir sekme eski token ile hemen gelirse: access token alır, cookie değişmez.
        MockHttpServletResponse concurrent = refresh(first).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(concurrent.getCookie(RefreshCookies.NAME)).isNull();

        // Pencere geçtikten sonra eski token: çalınmış sayılır, oturum kapanır.
        Thread.sleep(2_500);
        expectError(refresh(first), 401, AuthMessages.SESSION_EXPIRED);
        expectError(refresh(second), 401, AuthMessages.SESSION_EXPIRED);
    }

    @Test
    void logoutRevokesSession() throws Exception {
        String token = refreshCookie(login(ADMIN_EMAIL, ADMIN_SECRET).andReturn().getResponse());

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshCookies.NAME, token)))
                .andExpect(status().isOk());

        expectError(refresh(token), 401, AuthMessages.SESSION_EXPIRED);
    }

    @Test
    void fiveWrongPasswordsBlockLoginEvenWithCorrectPassword() throws Exception {
        String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_SECRET));
        String email = createUser(adminToken, "USER");

        for (int i = 0; i < 5; i++) {
            expectError(login(email, "wrong-password"), 401, AuthMessages.INVALID_CREDENTIALS);
        }
        expectError(login(email, "User12345"), 429, AuthMessages.LOGIN_BLOCKED);
    }

    @Test
    void userRoleCannotUseAdminEndpointsAndDoesNotSeeAdminMenus() throws Exception {
        String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_SECRET));
        String email = createUser(adminToken, "USER");

        ResultActions userLogin = login(email, "User12345")
                .andExpect(jsonPath("$.data.menus[*].titleKey", not(hasItem("menu_administration"))));
        String userToken = accessToken(userLogin);

        expectError(withToken(get("/api/users"), userToken), 403, GlobalMessages.ACCESS_DENIED);
        mockMvc.perform(withToken(get("/api/units"), userToken)).andExpect(status().isOk());
    }

    @Test
    void requestWithoutTokenReturns401InApiFormat() throws Exception {
        expectError(get("/api/units"), 401, GlobalMessages.UNAUTHORIZED);
    }

    @Test
    void readinessEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void unitCrudWithVersionConflictAndSoftDelete() throws Exception {
        String token = accessToken(login(ADMIN_EMAIL, ADMIN_SECRET));
        String name = "Birim " + UUID.randomUUID();

        String created = mockMvc.perform(withToken(post("/api/units"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GlobalMessages.RECORD_CREATED))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.data.id");

        expectError(withToken(post("/api/units"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"), 422, UnitMessages.NAME_EXISTS);

        mockMvc.perform(withToken(put("/api/units/" + id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + " v2\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        expectError(withToken(put("/api/units/" + id), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + " v3\",\"version\":0}"), 409, GlobalMessages.RECORD_MODIFIED);

        mockMvc.perform(withToken(delete("/api/units/" + id), token)).andExpect(status().isOk());
        expectError(withToken(get("/api/units/" + id), token), 404, GlobalMessages.RECORD_NOT_FOUND);

        Boolean deleted = jdbc.queryForObject("SELECT deleted FROM units WHERE id = ?", Boolean.class, id);
        assertThat(deleted).isTrue();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookies.NAME, refreshToken)));
    }

    private String createUser(String adminToken, String role) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        mockMvc.perform(withToken(post("/api/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Test User\",\"password\":\"User12345\",\"role\":\"" + role
                                + "\",\"unitId\":" + testUnitId() + "}"))
                .andExpect(status().isOk());
        return email;
    }

    private long testUnitId() {
        jdbc.update("""
                INSERT INTO units (name, version, deleted, created_at)
                SELECT 'Test Birimi', 0, false, now() WHERE NOT EXISTS (SELECT 1 FROM units WHERE name = 'Test Birimi')""");
        return jdbc.queryForObject("SELECT id FROM units WHERE name = 'Test Birimi' AND deleted = false", Long.class);
    }

    private void expectError(MockHttpServletRequestBuilder request, int status, String message) throws Exception {
        expectError(mockMvc.perform(request), status, message);
    }

    private static void expectError(ResultActions result, int status, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String accessToken(ResultActions loginResult) throws Exception {
        return JsonPath.read(loginResult.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }

    private static String refreshCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(RefreshCookies.NAME);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }
}
