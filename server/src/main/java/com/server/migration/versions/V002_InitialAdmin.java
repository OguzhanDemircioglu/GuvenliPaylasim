package com.server.migration.versions;

import com.server.migration.AdminProperties;
import com.server.migration.DataMigration;
import com.server.modules.user.Emails;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * İlk admin kullanıcısı. ADMIN_EMAIL ve ADMIN_PASSWORD tanımlı değilse uygulama açılmaz;
 * varsayılan bir şifreyle admin oluşturulmaz.
 */
@Component
@RequiredArgsConstructor
public class V002_InitialAdmin implements DataMigration {

    private final AdminProperties admin;
    private final PasswordEncoder passwordEncoder;

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "İlk admin kullanıcısı";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        Integer admins = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE role = 'ADMIN' AND deleted = false", Integer.class);
        if (admins != null && admins > 0) {
            return;
        }
        if (isBlank(admin.email()) || isBlank(admin.password())) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD must be set to create the initial admin user");
        }
        jdbc.update("""
                        INSERT INTO users (email, password_hash, full_name, role, active, version, deleted, created_at)
                        VALUES (?, ?, ?, 'ADMIN', true, 0, false, now())""",
                Emails.normalize(admin.email()), passwordEncoder.encode(admin.password()), "Sistem Yöneticisi");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
