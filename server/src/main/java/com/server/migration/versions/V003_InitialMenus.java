package com.server.migration.versions;

import com.server.migration.DataMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Başlangıç menüleri. Tanımlar grubu herkese, Yönetim grubu sadece ADMIN'e görünür.
 */
@Component
public class V003_InitialMenus implements DataMigration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Başlangıç menüleri";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO menus (parent_id, title_key, route, icon, sort_order, admin_only, version, deleted, created_at)
                VALUES (NULL, 'menu_definitions', NULL, 'settings', 1, false, 0, false, now()),
                       (NULL, 'menu_administration', NULL, 'shield', 99, true, 0, false, now())""");
        jdbc.update("""
                INSERT INTO menus (parent_id, title_key, route, icon, sort_order, admin_only, version, deleted, created_at)
                VALUES ((SELECT id FROM menus WHERE title_key = 'menu_administration' AND deleted = false),
                        'menu_users', '/users', 'users', 1, true, 0, false, now())""");
    }
}
