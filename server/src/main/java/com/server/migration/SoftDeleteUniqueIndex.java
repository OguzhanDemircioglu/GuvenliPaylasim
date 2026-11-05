package com.server.migration;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Soft delete kullanılan tablolarda benzersizlik: sadece silinmemiş kayıtlar arasında.
 * PostgreSQL'de partial index oluşturur. H2'de partial index yoktur; aynı sonuç, silinmiş kayıtlarda NULL olan
 * üretilmiş bir kolon üzerindeki unique index ile alınır (unique index NULL'ları çakışma saymaz).
 */
public final class SoftDeleteUniqueIndex {

    private SoftDeleteUniqueIndex() {
    }

    /** Kolon metin tipinde olmalıdır. */
    public static void create(JdbcTemplate jdbc, String name, String table, String column) {
        String product = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if ("PostgreSQL".equals(product)) {
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS %s ON %s (%s) WHERE deleted = false".formatted(name, table, column));
            return;
        }
        String activeColumn = "active_" + column;
        jdbc.execute("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s VARCHAR GENERATED ALWAYS AS (CASE WHEN deleted = false THEN %s END)"
                .formatted(table, activeColumn, column));
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS %s ON %s (%s)".formatted(name, table, activeColumn));
    }
}
