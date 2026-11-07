package com.server.modules.user;

/**
 * Ad soyad kuralı: harf (Türkçe dahil), boşluk, kesme, nokta ve tire; rakam ve başka işaret yoktur. En az iki, en fazla
 * üç kelime; her kelime harfle başlar. Masaüstündeki {@code Inputs} ile aynı kural. Kelime başlarını masaüstü büyütür;
 * sunucu sadece denetler.
 */
public final class FullNames {

    /** İzin verilen karakterler; ilk karakter harf. */
    public static final String PATTERN = "^\\p{L}[\\p{L} .'\\-]*$";
    /** 2–3 kelime, tek boşlukla; her kelime harfle başlar. */
    public static final String WORDS_PATTERN = "^\\p{L}[\\p{L}.'\\-]*( \\p{L}[\\p{L}.'\\-]*){1,2}$";
    public static final int MAX_LENGTH = 30;

    private FullNames() {
    }

    /** Kenar boşlukları atılır, art arda boşluklar teke indirilir. */
    public static String trim(String fullName) {
        return fullName == null ? null : fullName.trim().replaceAll("\\s+", " ");
    }
}
