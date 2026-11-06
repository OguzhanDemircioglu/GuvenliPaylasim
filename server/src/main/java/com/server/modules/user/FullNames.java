package com.server.modules.user;

/**
 * Ad soyad kuralı: harfle başlar; harf (Türkçe dahil), boşluk, kesme, nokta ve tire. Rakam ve başka işaret yoktur.
 * Masaüstündeki {@code Inputs} ile aynı kural.
 */
public final class FullNames {

    public static final String PATTERN = "^\\p{L}[\\p{L} .'\\-]*$";
    public static final int MAX_LENGTH = 100;

    private FullNames() {
    }

    public static String trim(String fullName) {
        return fullName == null ? null : fullName.trim();
    }
}
