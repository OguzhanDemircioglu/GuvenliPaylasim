package com.server.migration.versions;

import com.server.migration.DataMigration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Crypto_Design §8.5: silinen kutu iz bırakmaz. Eski "silindi" işaretli satırlar ({@code deleted_at},
 * {@code purged_at}) gerçekten silinir ve bu kolonlar kaldırılır. Kolonlar yoksa (yeni kurulum) bir şey yapmaz.
 */
@Component
public class V002_HardDelete implements DataMigration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Silinmiş işaretli kutuları kalıcı olarak sil, deleted_at/purged_at kolonlarını kaldır";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        if (hasColumn(jdbc, "document_recipients", "deleted_at")) {
            jdbc.execute("DELETE FROM document_recipients WHERE deleted_at IS NOT NULL");
            jdbc.execute("ALTER TABLE document_recipients DROP COLUMN deleted_at");
        }
        if (hasColumn(jdbc, "documents", "purged_at")) {
            jdbc.execute("""
                    DELETE FROM documents d
                     WHERE d.purged_at IS NOT NULL
                        OR NOT EXISTS (SELECT 1 FROM document_recipients r WHERE r.document_id = d.id)""");
            jdbc.execute("ALTER TABLE documents DROP COLUMN purged_at");
        }
    }

    private static boolean hasColumn(JdbcTemplate jdbc, String table, String column) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = current_schema AND table_name = ? AND column_name = ?""",
                Integer.class, table, column);
        return count != null && count > 0;
    }
}
