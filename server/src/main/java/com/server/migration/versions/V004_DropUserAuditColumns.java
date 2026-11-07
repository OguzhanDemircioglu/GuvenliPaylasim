package com.server.migration.versions;

import com.server.migration.DataMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code users.created_by} ve {@code users.updated_by} hiçbir yerde okunmuyordu: kayıt girişsiz yapıldığı için
 * {@code created_by} hep boştu, {@code updated_by} de hep kişinin kendisiydi. Entity'den çıkarıldılar; Hibernate
 * ({@code ddl-auto: update}) kolon silmediği için burada kaldırılır. Kolonlar yoksa (yeni kurulum) bir şey yapmaz.
 */
@Component
public class V004_DropUserAuditColumns implements DataMigration {

    @Override
    public int version() {
        return 4;
    }

    @Override
    public String description() {
        return "users.created_by ve users.updated_by kolonlarını kaldır";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE users DROP COLUMN IF EXISTS created_by");
        jdbc.execute("ALTER TABLE users DROP COLUMN IF EXISTS updated_by");
    }
}
