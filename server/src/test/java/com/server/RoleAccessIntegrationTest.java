package com.server;

import com.jayway.jsonpath.JsonPath;
import com.server.common.message.GlobalMessages;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rol hiyerarşisi, yol kuralları ve yönetim kapsamı uçtan uca (PostgreSQL 18; Docker yoksa atlanır).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RoleAccessIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String SUPERADMIN_EMAIL = "superadmin@test.local";
    private static final String PASSWORD = "User12345";
    private static final String SUPERADMIN_SECRET = AuthSecrets.derive(SUPERADMIN_EMAIL, PASSWORD);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("DB_SCHEMA", () -> "starterapp");
        registry.add("app.admin.email", () -> SUPERADMIN_EMAIL);
        registry.add("app.admin.password", () -> PASSWORD);
        registry.add("app.session.cookie-secure", () -> "false");
        TestJwtKeys.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void superadminInheritsAdminAndUserAndSeesAllMenus() throws Exception {
        String token = accessToken(login(SUPERADMIN_EMAIL)
                .andExpect(jsonPath("$.data.user.role").value("SUPERADMIN"))
                .andExpect(jsonPath("$.data.menus[*].titleKey", hasItem("menu_administration"))));

        mockMvc.perform(withToken(get("/api/users"), token)).andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/units"), token)).andExpect(status().isOk());
    }

    @Test
    void adminInheritsUser() throws Exception {
        String admin = accessToken(login(createUser(superadminToken(), "ADMIN")));

        mockMvc.perform(withToken(get("/api/users"), admin)).andExpect(status().isOk());
        mockMvc.perform(withToken(get("/api/units"), admin)).andExpect(status().isOk());
    }

    @Test
    void ownerIsReadOnlyAndSeesOnlyHome() throws Exception {
        String owner = accessToken(login(createUser(superadminToken(), "OWNER"))
                .andExpect(jsonPath("$.data.menus[*].titleKey", containsInAnyOrder("menu_home"))));

        expectError(withToken(get("/api/units"), owner), 403, GlobalMessages.ACCESS_DENIED);
        expectError(withToken(post("/api/units"), owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"), 403, GlobalMessages.ACCESS_DENIED);
        expectError(withToken(get("/api/users"), owner), 403, GlobalMessages.ACCESS_DENIED);
    }

    @Test
    void userCannotReachAdminEndpoints() throws Exception {
        String user = accessToken(login(createUser(superadminToken(), "USER"))
                .andExpect(jsonPath("$.data.menus[*].titleKey", not(hasItem("menu_administration")))));

        expectError(withToken(get("/api/users"), user), 403, GlobalMessages.ACCESS_DENIED);
        mockMvc.perform(withToken(get("/api/units"), user)).andExpect(status().isOk());
    }

    @Test
    void adminManagesOnlyUsersAndCannotAssignRoles() throws Exception {
        String superadmin = superadminToken();
        String admin = accessToken(login(createUser(superadmin, "ADMIN")));
        long otherAdmin = userId(createUser(superadmin, "ADMIN"));
        long owner = userId(createUser(superadmin, "OWNER"));
        long user = userId(createUser(superadmin, "USER"));

        expectError(createUserRequest(admin, "ADMIN"), 422, UserMessages.ROLE_NOT_ASSIGNABLE);
        expectError(createUserRequest(admin, "OWNER"), 422, UserMessages.ROLE_NOT_ASSIGNABLE);
        createUserRequest(admin, "USER").andExpect(status().isOk());

        expectError(update(admin, otherAdmin, "ADMIN", true), 422, UserMessages.NOT_MANAGEABLE);
        expectError(update(admin, owner, "OWNER", true), 422, UserMessages.NOT_MANAGEABLE);
        expectError(withToken(delete("/api/users/" + otherAdmin), admin), 422, UserMessages.NOT_MANAGEABLE);
        expectError(withToken(put("/api/users/" + owner + "/password"), admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Other12345\"}"),
                422, UserMessages.NOT_MANAGEABLE);

        expectError(update(admin, user, "ADMIN", true), 422, UserMessages.ROLE_NOT_ASSIGNABLE);
        update(admin, user, "USER", false).andExpect(status().isOk());
    }

    @Test
    void superadminChangesRolesButNeverGrantsOrLosesSuperadmin() throws Exception {
        String superadmin = superadminToken();
        String email = createUser(superadmin, "USER");
        long id = userId(email);

        update(superadmin, id, "OWNER", true).andExpect(status().isOk());
        login(email).andExpect(jsonPath("$.data.user.role").value("OWNER"));

        expectError(update(superadmin, id, "SUPERADMIN", true), 422, UserMessages.ROLE_NOT_ASSIGNABLE);
        expectError(createUserRequest(superadmin, "SUPERADMIN"), 422, UserMessages.ROLE_NOT_ASSIGNABLE);

        long self = userId(SUPERADMIN_EMAIL);
        expectError(update(superadmin, self, "ADMIN", true), 422, UserMessages.ROLE_NOT_ASSIGNABLE);
        expectError(update(superadmin, self, "SUPERADMIN", false), 422, UserMessages.LAST_SUPERADMIN);
        expectError(withToken(delete("/api/users/" + self), superadmin), 422, UserMessages.LAST_SUPERADMIN);
    }

    private String superadminToken() throws Exception {
        return accessToken(login(SUPERADMIN_EMAIL));
    }

    /** İlk süperadmin migration'la türetilmiş parolayla açılır; API'den açılanlar testin gönderdiği değerle. */
    private ResultActions login(String email) throws Exception {
        String password = SUPERADMIN_EMAIL.equals(email) ? SUPERADMIN_SECRET : PASSWORD;
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
    }

    private long testUnitId() {
        jdbc.update("""
                INSERT INTO units (name, version, deleted, created_at)
                SELECT 'Test Birimi', 0, false, now() WHERE NOT EXISTS (SELECT 1 FROM units WHERE name = 'Test Birimi')""");
        return jdbc.queryForObject("SELECT id FROM units WHERE name = 'Test Birimi'", Long.class);
    }

    private String createUser(String token, String role) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        createUserRequest(token, role, email).andExpect(status().isOk());
        return email;
    }

    private ResultActions createUserRequest(String token, String role) throws Exception {
        return createUserRequest(token, role, "user-" + UUID.randomUUID() + "@test.local");
    }

    private ResultActions createUserRequest(String token, String role, String email) throws Exception {
        return mockMvc.perform(withToken(post("/api/users"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"fullName\":\"Test User\",\"password\":\"" + PASSWORD
                        + "\",\"role\":\"" + role + "\",\"unitId\":" + testUnitId() + "}"));
    }

    private ResultActions update(String token, long id, String role, boolean active) throws Exception {
        String body = mockMvc.perform(withToken(get("/api/users/" + id), superadminToken()))
                .andReturn().getResponse().getContentAsString();
        String email = JsonPath.read(body, "$.data.email");
        Integer version = JsonPath.read(body, "$.data.version");
        return mockMvc.perform(withToken(put("/api/users/" + id), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"fullName\":\"Test User\",\"role\":\"" + role
                        + "\",\"active\":" + active + ",\"unitId\":" + testUnitId() + ",\"version\":" + version + "}"));
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ? AND deleted = false", Long.class, email);
    }

    private void expectError(MockHttpServletRequestBuilder request, int status, String message) throws Exception {
        expectError(mockMvc.perform(request), status, message);
    }

    private static void expectError(ResultActions result, int status, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String accessToken(ResultActions loginResult) throws Exception {
        return JsonPath.read(loginResult.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }
}
