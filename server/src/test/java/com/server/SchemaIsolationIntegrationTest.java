package com.server;

import com.server.support.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paylaşılan veritabanı güvencesi: başka bir uygulama public.categories ve public.users tablolarına sahipken
 * DB_SCHEMA=starterapp ile açılan uygulama onlara dokunmamalı. Docker yoksa otomatik olarak atlanır.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SchemaIsolationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withInitScript("schema-isolation-init.sql");

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

    @Test
    void otherSchemasAreUntouched() {
        assertThat(columns("public", "categories")).containsExactly("id", "legacy_name");
        assertThat(columns("public", "users")).containsExactly("id", "login");
        assertThat(jdbc.queryForObject("SELECT legacy_name FROM public.categories WHERE id = 1", String.class))
                .isEqualTo("other app row");
        assertThat(tables("public")).containsExactly("categories", "users");

        assertThat(tables("starterapp")).containsExactly(
                "categories", "data_migration_history", "login_attempts", "menus", "user_sessions", "users");
    }

    private List<String> columns(String schema, String table) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position""", String.class, schema, table);
    }

    private List<String> tables(String schema) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY 1", String.class, schema);
    }
}
