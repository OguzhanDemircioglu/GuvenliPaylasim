package com.server.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Hibernate enum kolonlarına (EnumType.STRING) {@code check (kolon in (...))} constraint'i koyar ve enum'a değer
 * eklendiğinde bu constraint'i güncellemez (Backend_Architecture §10.1). Bu yardımcı, kolonun mevcut check
 * constraint'ini (adı veritabanına göre değişir) bulup siler ve verilen değerlerle yeniden oluşturur.
 * PostgreSQL ve H2'de aynı şekilde çalışır.
 */
public final class EnumCheckConstraint {

    private EnumCheckConstraint() {
    }

    /**
     * @param name   yeni constraint'in adı (ör. "users_role_check")
     * @param values kolonun alabileceği bütün değerler; migration'a sabit olarak yazılır, enum'dan okunmaz
     */
    public static void replace(JdbcTemplate jdbc, String table, String column, String name, List<String> values) {
        // NOT NULL da bazı veritabanlarında check constraint olarak listelenir; onlar hariç tutulur.
        List<String> existing = jdbc.queryForList("""
                        SELECT tc.constraint_name
                          FROM information_schema.table_constraints tc
                          JOIN information_schema.check_constraints cc
                            ON cc.constraint_schema = tc.constraint_schema
                           AND cc.constraint_name = tc.constraint_name
                         WHERE tc.table_schema = current_schema
                           AND lower(tc.table_name) = ?
                           AND tc.constraint_type = 'CHECK'
                           AND lower(cc.check_clause) LIKE ?
                           AND upper(cc.check_clause) NOT LIKE '%IS NOT NULL%'""",
                String.class, table.toLowerCase(), "%" + column.toLowerCase() + "%");
        for (String constraint : existing) {
            jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + constraint + "\"");
        }
        String list = values.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", "));
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " CHECK (" + column + " IN (" + list + "))");
    }
}
