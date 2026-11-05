package com.server.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hibernate şemayı oluşturduktan sonra, uygulanmamış DataMigration sürümlerini sırayla çalıştırır.
 * - Her sürüm kendi transaction'ında çalışır; hata olursa geri alınır ve uygulama açılmaz.
 * - Uygulanan sürümler data_migration_history tablosuna yazılır ve bir daha çalışmaz.
 * - PostgreSQL advisory lock sayesinde aynı anda açılan iki replica aynı sürümü iki kez çalıştıramaz.
 *   Gömülü H2'yi tek süreç kullandığı için orada kilit alınmaz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationRunner implements ApplicationRunner {

    /** Bu uygulamaya ait sabit kilit numarası. */
    private static final long LOCK_ID = 7_402_311_001L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final List<DataMigration> migrations;

    @Override
    public void run(ApplicationArguments args) {
        List<DataMigration> ordered = migrations.stream()
                .sorted(Comparator.comparingInt(DataMigration::version))
                .toList();
        checkUniqueVersions(ordered);

        // Kilit, ayrı tutulan tek bir bağlantı üzerinde alınır ve iş bitince bırakılır.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                applyPending(ordered);
                return null;
            }
            execute(connection, "SELECT pg_advisory_lock(" + LOCK_ID + ")");
            try {
                applyPending(ordered);
            } finally {
                execute(connection, "SELECT pg_advisory_unlock(" + LOCK_ID + ")");
            }
            return null;
        });
    }

    private void applyPending(List<DataMigration> ordered) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_migration_history (
                    version     INTEGER PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    applied_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
                )""");
        Set<Integer> applied = new HashSet<>(jdbc.queryForList("SELECT version FROM data_migration_history", Integer.class));

        for (DataMigration migration : ordered) {
            if (applied.contains(migration.version())) {
                continue;
            }
            log.info("Applying data migration V{}: {}", migration.version(), migration.description());
            transactionTemplate.executeWithoutResult(status -> {
                migration.migrate(jdbc);
                jdbc.update("INSERT INTO data_migration_history (version, description) VALUES (?, ?)",
                        migration.version(), migration.description());
            });
        }
    }

    private static void checkUniqueVersions(List<DataMigration> ordered) {
        Set<Integer> seen = new HashSet<>();
        for (DataMigration migration : ordered) {
            if (!seen.add(migration.version())) {
                throw new IllegalStateException("Duplicate data migration version: " + migration.version());
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
