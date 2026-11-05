package com.server.migration.versions;

import com.server.migration.AdminProperties;
import com.server.migration.DataMigration;
import com.server.modules.user.Emails;
import com.server.security.AuthSecrets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sadece dev profilinde: demo için birimler ve birimlere bağlı kullanıcılar. Prod'da çalışmaz; orada birimleri ve
 * kullanıcıları yönetici ekrandan açar. Kullanıcıların anahtarları ilk girişte kendi bilgisayarlarında üretilir,
 * bu yüzden burada anahtar oluşturulmaz.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class V007_DemoData implements DataMigration {

    static final String DEMO_PASSWORD = "Demo12345!";

    private static final List<String> UNITS = List.of(
            "Bilgi İşlem Dairesi Başkanlığı", "Ankara Valiliği", "İstanbul Valiliği", "İzmir Valiliği",
            "Bursa Valiliği", "Antalya Valiliği", "Erzurum Valiliği");

    private record DemoUser(String email, String fullName, String unit, String phone) {
    }

    private static final List<DemoUser> USERS = List.of(
            new DemoUser("ayse.yilmaz@gps.local", "Ayşe Yılmaz", "Ankara Valiliği", "05550000001"),
            new DemoUser("mehmet.kaya@gps.local", "Mehmet Kaya", "İstanbul Valiliği", "05550000002"),
            new DemoUser("zeynep.demir@gps.local", "Zeynep Demir", "İstanbul Valiliği", "05550000003"),
            new DemoUser("ali.celik@gps.local", "Ali Çelik", "İzmir Valiliği", "05550000004"));

    private final AdminProperties admin;
    private final PasswordEncoder passwordEncoder;

    @Override
    public int version() {
        return 7;
    }

    @Override
    public String description() {
        return "Demo birimleri ve kullanıcıları (sadece dev)";
    }

    @Override
    public void migrate(JdbcTemplate jdbc) {
        for (String unit : UNITS) {
            jdbc.update("INSERT INTO units (name, version, deleted, created_at) VALUES (?, 0, false, now())", unit);
        }
        jdbc.update("UPDATE users SET unit_id = (SELECT id FROM units WHERE name = ? AND deleted = false) WHERE email = ?",
                UNITS.getFirst(), Emails.normalize(admin.email()));

        for (DemoUser user : USERS) {
            jdbc.update("""
                            INSERT INTO users (email, password_hash, full_name, role, active, unit_id, phone,
                                               must_change_password, version, deleted, created_at)
                            VALUES (?, ?, ?, 'USER', true, (SELECT id FROM units WHERE name = ? AND deleted = false), ?,
                                    false, 0, false, now())""",
                    user.email(), passwordEncoder.encode(AuthSecrets.derive(user.email(), DEMO_PASSWORD)),
                    user.fullName(), user.unit(), user.phone());
        }
    }
}
