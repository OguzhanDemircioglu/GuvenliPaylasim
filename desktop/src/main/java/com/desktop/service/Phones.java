package com.desktop.service;

import java.util.regex.Pattern;

/**
 * Cep telefonu numaraları (docs/Crypto_Design.md §7.2). Kullanıcı 10 hane girer ({@code 5XXXXXXXXX}); başındaki 0,
 * +90, boşluk ve tireler kabul edilip atılır. Sunucuya {@code 5XXXXXXXXX}, parola türetmeye E.164 gider.
 */
public final class Phones {

    private static final Pattern INPUT = Pattern.compile("^5\\d{9}$");

    private Phones() {
    }

    /** Girişi {@code 5XXXXXXXXX} biçimine getirir; geçersizse null. */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+90")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return INPUT.matcher(digits).matches() ? digits : null;
    }

    /** {@code 5XXXXXXXXX} → {@code +905XXXXXXXXX}. */
    public static String toE164(String normalized) {
        return "+90" + normalized;
    }
}
