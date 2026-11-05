package com.server.migration.versions;

import com.server.migration.AdminProperties;
import com.server.migration.DataMigration;
import com.server.migration.EnumCheckConstraint;
import com.server.modules.user.Emails;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SUPERADMIN ve OWNER rolleri (docs/Security_Roadmap.md §4.1):
 * <ol>
 *   <li>users.role check constraint'i yeni rollerle yenilenir.</li>
 *   <li>Süperadmin yoksa ilk admin (ADMIN_EMAIL, bulunamazsa en eski aktif admin) SUPERADMIN yapılır. Süperadmin
 *       ekrandan oluşturulamaz; tek yolu budur.</li>
 *   <li>Menüler admin_only yerine roles kolonunu kullanır. admin_only'ye varsayılan değer verilir ki yeni
 *       migration'lar onu yazmak zorunda kalmasın.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class V006_SuperadminAndOwnerRoles implements DataMigration {

    private final AdminProperties admin;

    @Override
    public int version() {
        return 6;
    }

    @Override
    public String description() {
        return "SUPERADMIN ve OWNER rolleri, menü rol kümeleri";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        EnumCheckConstraint.replace(jdbc, "users", "role", "users_role_check",
                List.of("SUPERADMIN", "ADMIN", "OWNER", "USER"));
        promoteInitialAdmin(jdbc);

        jdbc.execute("ALTER TABLE menus ALTER COLUMN admin_only SET DEFAULT false");
        jdbc.update("UPDATE menus SET roles = CASE WHEN admin_only THEN 'ADMIN' ELSE 'USER' END");
        // Sahip de anasayfaya iner (toplu raporlar); kendi ekranları Faz 3'te gelir.
        jdbc.update("UPDATE menus SET roles = 'OWNER,USER' WHERE title_key = 'menu_home' AND deleted = false");
    }

    private void promoteInitialAdmin(JdbcTemplate jdbc) {
        Integer superadmins = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE role = 'SUPERADMIN' AND deleted = false", Integer.class);
        if (superadmins != null && superadmins > 0) {
            return;
        }
        int updated = 0;
        if (admin.email() != null && !admin.email().isBlank()) {
            updated = jdbc.update("""
                    UPDATE users SET role = 'SUPERADMIN', version = version + 1, updated_at = now()
                     WHERE email = ? AND role = 'ADMIN' AND deleted = false""", Emails.normalize(admin.email()));
        }
        if (updated == 0) {
            jdbc.update("""
                    UPDATE users SET role = 'SUPERADMIN', version = version + 1, updated_at = now()
                     WHERE id = (SELECT min(id) FROM users WHERE role = 'ADMIN' AND active = true AND deleted = false)""");
        }
    }
}
