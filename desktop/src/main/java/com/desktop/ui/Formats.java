package com.desktop.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formats {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", TR);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", TR);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", TR);

    private Formats() {
    }

    /** Bugünse saat, değilse gün ve ay. */
    public static String shortDate(Instant instant) {
        var local = instant.atZone(ZONE);
        return local.toLocalDate().equals(LocalDate.now(ZONE)) ? TIME.format(local) : DATE.format(local);
    }

    public static String fullDate(Instant instant) {
        return FULL.format(instant.atZone(ZONE));
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(TR, "%.1f KB", bytes / 1024.0);
        }
        return String.format(TR, "%.1f MB", bytes / (1024.0 * 1024));
    }

    /** "Ayşe Yılmaz" → "AY" */
    public static String initials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(TR);
    }
}
