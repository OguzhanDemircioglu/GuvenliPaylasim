package com.server.modules.report;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rapor sorguları native SQL'dir (Backend_Architecture.md §5.5):
 * - Toplama SQL'de yapılır, frontend'e sadece özet döner.
 * - Günler Türkiye saatine göre ayrılır; UTC'ye göre gruplamak gece 00:00-03:00 kayıtlarını önceki güne yazardı.
 * - Kayıt olmayan günler 0 olarak eklenir (generate_series H2'de olmadığı için Java'da).
 * - Native SQL'de soft delete filtresi otomatik değildir; "deleted = false" elle yazılır.
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final JdbcTemplate jdbc;

    @Override
    public List<PeriodValue> categoriesCreatedPerDay(int days) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(days - 1L);
        Map<LocalDate, Long> totals = new HashMap<>();
        jdbc.query("""
                        SELECT CAST(created_at AT TIME ZONE 'Europe/Istanbul' AS DATE) AS period, count(*) AS total
                          FROM categories
                         WHERE deleted = false
                           AND created_at >= ?
                         GROUP BY period""",
                (RowCallbackHandler) rs -> totals.put(rs.getObject("period", LocalDate.class), rs.getLong("total")),
                first.atStartOfDay(ZONE).toOffsetDateTime());
        return first.datesUntil(today.plusDays(1))
                .map(day -> new PeriodValue(day.toString(), totals.getOrDefault(day, 0L)))
                .toList();
    }
}
