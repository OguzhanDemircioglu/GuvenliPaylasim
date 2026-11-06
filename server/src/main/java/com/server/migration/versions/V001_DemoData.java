package com.server.migration.versions;

import com.server.migration.DataMigration;
import com.server.security.AuthSecrets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sadece dev profilinde: demo kullanıcıları (v2, docs/Crypto_Design.md §7.11). Prod'da çalışmaz; orada herkes
 * kendisi kayıt olur. Anahtarlar istemcide üretilir, bu yüzden burada anahtar oluşturulmaz.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class V001_DemoData implements DataMigration {

    /** En az 12 karakter (Security.md S2). */
    static final String DEMO_PASSWORD = "DemoParola2026!";

    /** DevLoginController bu kullanıcıyla oturum açar. */
    public static final String DEV_LOGIN_PHONE = "+905550000001";

    private record DemoUser(String phone, String fullName) {
    }

    private static final List<DemoUser> USERS = List.of(
            new DemoUser(DEV_LOGIN_PHONE, "Ayşe Yılmaz"),
            new DemoUser("+905550000002", "Mehmet Kaya"),
            new DemoUser("+905550000003", "Zeynep Demir"),
            new DemoUser("+905550000004", "Ali Çelik"));

    private final PasswordEncoder passwordEncoder;

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Demo kullanıcıları (sadece dev)";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        for (DemoUser user : USERS) {
            jdbc.update("""
                            INSERT INTO users (phone, password_hash, full_name, version, created_at)
                            VALUES (?, ?, ?, 0, now())""",
                    user.phone(), passwordEncoder.encode(AuthSecrets.derive(user.phone(), DEMO_PASSWORD)),
                    user.fullName());
        }
    }
}
