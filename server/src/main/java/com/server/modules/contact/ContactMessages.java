package com.server.modules.contact;

public final class ContactMessages {

    /** Alıcı kişi değil, doğrulanmamış ya da anahtarı değişmiş: davet koduyla gönderilmeli. */
    public static final String NOT_VERIFIED = "contact_not_verified";
    public static final String FINGERPRINT_MISMATCH = "contact_fingerprint_mismatch";

    private ContactMessages() {
    }
}
