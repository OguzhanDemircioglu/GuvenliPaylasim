package com.server.modules.user;

import java.util.Locale;

/**
 * E-posta adresleri küçük harfe çevrilip trim edilerek saklanır ve karşılaştırılır.
 * Locale.ROOT bilinçli: Türkçe locale'de "I" harfi "ı" olurdu ve adres bozulurdu.
 * Türkçe klavyeler "i" harfini "İ" olarak büyütebildiği için (ör. telefonda otomatik büyük harf) "İ" de "i" sayılır.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().replace('İ', 'i').toLowerCase(Locale.ROOT);
    }
}
