package com.server.modules.verification;

/** Telefon numarası loglarda tam görünmez (KVKK). */
public final class Masks {

    private Masks() {
    }

    public static String phone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.length() <= 4 ? "****" : "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
