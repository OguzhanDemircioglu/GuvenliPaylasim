package com.server.modules.document;

import java.util.Set;

/** Kutunun ömrü (Crypto_Design §8.2): 1 saat, 1 gün, 7 gün (varsayılan), 30 gün. Süresiz kutu yoktur. */
public final class BoxLifetimes {

    public static final Set<Integer> HOURS = Set.of(1, 24, 168, 720);

    private BoxLifetimes() {
    }

    public static boolean valid(Integer hours) {
        return hours != null && HOURS.contains(hours);
    }
}
