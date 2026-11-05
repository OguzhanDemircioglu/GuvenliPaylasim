package com.server.migration;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Soft delete kullanılan tablolarda benzersizlik: sadece silinmemiş kayıtlar arasında (PostgreSQL partial index).
 */
public final class SoftDeleteUniqueIndex {

    private SoftDeleteUniqueIndex() {
    }

    /** Kolon metin tipinde olmalıdır. */
    public static void create(JdbcTemplate jdbc, String name, String table, String column) {
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS %s ON %s (%s) WHERE deleted = false".formatted(name, table, column));
    }
}
