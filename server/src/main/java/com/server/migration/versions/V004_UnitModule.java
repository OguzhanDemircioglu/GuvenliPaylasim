package com.server.migration.versions;

import com.server.migration.DataMigration;
import com.server.migration.SoftDeleteUniqueIndex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Referans modülün migration'ı: yeni bir modül eklerken bu class örnek alınır (index + menü kaydı).
 */
@Component
public class V004_UnitModule implements DataMigration {

    @Override
    public int version() {
        return 4;
    }

    @Override
    public String description() {
        return "Birim modülü: unique index ve menü";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        SoftDeleteUniqueIndex.create(jdbc, "ux_units_name", "units", "name");
        jdbc.update("""
                INSERT INTO menus (parent_id, title_key, route, icon, sort_order, admin_only, version, deleted, created_at)
                VALUES ((SELECT id FROM menus WHERE title_key = 'menu_definitions' AND deleted = false),
                        'menu_units', '/units', 'tag', 1, false, 0, false, now())""");
    }
}
