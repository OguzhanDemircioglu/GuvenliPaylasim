package com.server;

import com.server.modules.auth.LoginAttempt;
import com.server.modules.auth.LoginAttemptRepository;
import com.server.support.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration'lar, silinmemiş kayıtlara özel unique index ve yanlış deneme sayacı gerçek PostgreSQL 18 üzerinde.
 * Docker yoksa otomatik olarak atlanır.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DatabaseIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;

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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LoginAttemptRepository attempts;

    @Test
    void migrationsAreApplied() {
        List<Integer> versions = jdbc.queryForList("SELECT version FROM data_migration_history ORDER BY version", Integer.class);
        assertThat(versions).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void initialAdminIsPromotedToSuperadminAndNewRolesPassTheCheckConstraint() {
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = 'admin@test.local'", String.class))
                .isEqualTo("SUPERADMIN");

        insertUser("owner@test.local", "OWNER");
        assertThatThrownBy(() -> insertUser("bad@test.local", "ROOT")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void menusGetRoleSetsFromAdminOnly() {
        assertThat(menuRoles("menu_home")).isEqualTo("OWNER,USER");
        assertThat(menuRoles("menu_definitions")).isEqualTo("USER");
        assertThat(menuRoles("menu_users")).isEqualTo("ADMIN");

        // Yeni migration'lar admin_only'yi yazmak zorunda değil (V006 varsayılan verdi).
        jdbc.update("""
                INSERT INTO menus (parent_id, title_key, route, icon, sort_order, roles, version, deleted, created_at)
                VALUES (NULL, 'menu_pg_test', NULL, NULL, 50, 'SUPERADMIN', 0, false, now())""");
        assertThat(menuRoles("menu_pg_test")).isEqualTo("SUPERADMIN");
    }

    @Test
    void uniqueIndexAppliesOnlyToNotDeletedRows() {
        insertUnit("Aynı Ad");
        assertThatThrownBy(() -> insertUnit("Aynı Ad")).isInstanceOf(DuplicateKeyException.class);

        jdbc.update("UPDATE units SET deleted = true WHERE name = 'Aynı Ad'");
        insertUnit("Aynı Ad");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM units WHERE name = 'Aynı Ad'", Integer.class)).isEqualTo(2);
    }

    @Test
    void failuresAreCountedThenBlockedAndResetAfterBlockExpires() {
        String key = "pg-test-key";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant blockedUntil = now.plus(1, ChronoUnit.HOURS);

        attempts.recordFailure(key, now, blockedUntil, MAX_ATTEMPTS);
        attempts.recordFailure(key, now, blockedUntil, MAX_ATTEMPTS);
        assertThat(attempt(key).isBlocked(now)).isFalse();

        attempts.recordFailure(key, now, blockedUntil, MAX_ATTEMPTS);
        assertThat(attempt(key).isBlocked(now)).isTrue();

        Instant afterBlock = blockedUntil.plusSeconds(1);
        attempts.recordFailure(key, afterBlock, afterBlock.plus(1, ChronoUnit.HOURS), MAX_ATTEMPTS);
        assertThat(attempt(key).isBlocked(afterBlock)).isFalse();
        assertThat(jdbc.queryForObject("SELECT fail_count FROM login_attempts WHERE attempt_key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    private LoginAttempt attempt(String key) {
        return attempts.findById(key).orElseThrow();
    }

    private void insertUser(String email, String role) {
        jdbc.update("""
                INSERT INTO users (email, password_hash, full_name, role, active, version, deleted, created_at)
                VALUES (?, 'x', 'Test', ?, true, 0, false, now())""", email, role);
    }

    private String menuRoles(String titleKey) {
        return jdbc.queryForObject("SELECT roles FROM menus WHERE title_key = ? AND deleted = false", String.class, titleKey);
    }

    private void insertUnit(String name) {
        jdbc.update("INSERT INTO units (name, version, deleted, created_at) VALUES (?, 0, false, now())", name);
    }
}
