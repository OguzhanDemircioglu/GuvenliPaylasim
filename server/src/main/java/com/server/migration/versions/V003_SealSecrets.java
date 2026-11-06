package com.server.migration.versions;

import com.server.migration.DataMigration;
import com.server.security.SealedColumns;
import com.server.security.ServerSeal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Security.md S2: ikinci kilitten önce yazılmış parola özetleri ve özel anahtar paketleri kilitlenir. Zaten kilitli
 * değerlere dokunulmaz; bu yüzden tekrar çalışsa da zararsızdır.
 */
@Component
@RequiredArgsConstructor
public class V003_SealSecrets implements DataMigration {

    private final ServerSeal seal;

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Parola özetlerini ve özel anahtar paketlerini sunucu anahtarıyla kilitle";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        jdbc.query("SELECT id, password_hash FROM users WHERE password_hash NOT LIKE 's1:%'", rs -> {
            jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?",
                    seal.seal(rs.getString("password_hash"), SealedColumns.PASSWORD_HASH), rs.getLong("id"));
        });
        jdbc.query("SELECT user_id, private_key_bundle FROM user_keys WHERE private_key_bundle NOT LIKE 's1:%'", rs -> {
            jdbc.update("UPDATE user_keys SET private_key_bundle = ? WHERE user_id = ?",
                    seal.seal(rs.getString("private_key_bundle"), SealedColumns.PRIVATE_KEY_BUNDLE),
                    rs.getLong("user_id"));
        });
    }
}
