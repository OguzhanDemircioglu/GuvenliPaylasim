package com.server.migration.versions;

import com.server.migration.DataMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Menü route'ları frontend'in klasör ve URL ağacıyla aynı hale gelir (Frontend_Architecture.md §3):
 * /categories → /definitions/categories, /users → /administration/users.
 * Anasayfa da menüye eklenir; sidebar tamamen DB'den gelir.
 * Menü ikonları NG-ZORRO'nun kullandığı Ant Design ikon adlarına çevrilir.
 */
@Component
public class V005_HierarchicalMenuRoutes implements DataMigration {

    @Override
    public int version() {
        return 5;
    }

    @Override
    public String description() {
        return "Hiyerarşik menü route'ları, anasayfa menüsü ve Ant Design ikonları";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        updateMenu(jdbc, "menu_definitions", "/definitions", "setting");
        updateMenu(jdbc, "menu_categories", "/definitions/categories", "tag");
        updateMenu(jdbc, "menu_administration", "/administration", "safety-certificate");
        updateMenu(jdbc, "menu_users", "/administration/users", "team");

        jdbc.update("""
                INSERT INTO menus (parent_id, title_key, route, icon, sort_order, admin_only, version, deleted, created_at)
                VALUES (NULL, 'menu_home', '/', 'home', 0, false, 0, false, now())""");
    }

    private static void updateMenu(JdbcTemplate jdbc, String titleKey, String route, String icon) {
        jdbc.update("""
                UPDATE menus SET route = ?, icon = ?, version = version + 1, updated_at = now()
                 WHERE title_key = ? AND deleted = false""", route, icon, titleKey);
    }
}
