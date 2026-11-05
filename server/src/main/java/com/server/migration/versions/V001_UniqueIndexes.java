package com.server.migration.versions;

import com.server.migration.DataMigration;
import com.server.migration.SoftDeleteUniqueIndex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Soft delete kullanılan tablolarda benzersizlik: sadece silinmemiş kayıtlar arasında.
 */
@Component
public class V001_UniqueIndexes implements DataMigration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Silinmemiş kayıtlara özel unique index'ler (users.email, menus.title_key)";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        SoftDeleteUniqueIndex.create(jdbc, "ux_users_email", "users", "email");
        SoftDeleteUniqueIndex.create(jdbc, "ux_menus_title_key", "menus", "title_key");
    }
}
