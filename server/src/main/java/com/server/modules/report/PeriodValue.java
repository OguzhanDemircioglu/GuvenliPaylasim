package com.server.modules.report;

/**
 * Zaman serisi grafiklerinin bir noktası.
 *
 * @param period dönem; günlük raporlarda "2026-10-29" (Türkiye saatine göre)
 * @param value  o dönemin değeri
 */
public record PeriodValue(String period, long value) {
}
