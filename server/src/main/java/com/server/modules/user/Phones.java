package com.server.modules.user;

import java.util.regex.Pattern;

/**
 * Cep telefonu numaraları (docs/Crypto_Design.md §7.2). Kullanıcı 10 hane girer ({@code 5XXXXXXXXX}, başında 0 ya da
 * +90 olmadan); veritabanında ve parola tuzunda E.164 biçimi ({@code +905XXXXXXXXX}) kullanılır.
 */
public final class Phones {

    /** İstekte kabul edilen biçim. */
    public static final String INPUT_PATTERN = "^5\\d{9}$";

    private static final Pattern INPUT = Pattern.compile(INPUT_PATTERN);

    private Phones() {
    }

    /** Boşlukları atar. Biçim kontrolü istekteki @Pattern ile yapılır. */
    public static String trim(String input) {
        return input == null ? null : input.replace(" ", "").trim();
    }

    /** {@code 5XXXXXXXXX} → {@code +905XXXXXXXXX}. Biçim geçersizse IllegalArgumentException. */
    public static String toE164(String input) {
        String trimmed = trim(input);
        if (trimmed == null || !INPUT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        return "+90" + trimmed;
    }
}
