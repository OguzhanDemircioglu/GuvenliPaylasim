package com.server.migration;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bir veri/şema sürümü. Her sürüm migration.versions paketinde V001_..., V002_... adında bir @Component'tir.
 * <p>
 * Kurallar (docs/Backend_Architecture.md §11):
 * <ol>
 *   <li>Uygulanmış bir sürüm değiştirilmez; değişiklik için yeni sürüm yazılır.</li>
 *   <li>Repository/entity kullanılmaz, sadece JdbcTemplate ile SQL yazılır.</li>
 *   <li>INSERT'lerde id yazılmaz; başka kayda alt sorgu ile referans verilir.</li>
 *   <li>BaseEntity kolonları açıkça verilir: version = 0, deleted = false, created_at = now().</li>
 * </ol>
 */
public interface DataMigration {

    int version();

    String description();

    void migrate(JdbcTemplate jdbc);
}
