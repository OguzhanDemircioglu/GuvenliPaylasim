package com.server.modules.invite;

public final class InviteMessages {

    public static final String PHONE_REQUIRED = "invite_phone_required";
    public static final String PHONE_INVALID = "invite_phone_invalid";
    public static final String OWN_PHONE = "invite_own_phone";
    public static final String TTL_INVALID = "invite_ttl_invalid";
    public static final String DAILY_LIMIT = "invite_daily_limit";
    public static final String FIELD_REQUIRED = "invite_field_required";
    public static final String FIELD_TOO_LONG = "invite_field_too_long";
    public static final String VERIFIER_INVALID = "invite_verifier_invalid";
    /** Kod yanlış, süresi dolmuş ya da bu numaraya ait değil: hepsi aynı cevabı alır. */
    public static final String CODE_INVALID = "invite_code_invalid";
    public static final String REDEEM_BLOCKED = "invite_redeem_blocked";

    private InviteMessages() {
    }
}
