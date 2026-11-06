package com.server.modules.totp;

public final class TotpMessages {

    public static final String CODE_REQUIRED = "totp_code_required";
    /** Authenticator kodu ya da kurtarma kodu hatalı, süresi geçmiş ya da daha önce kullanılmış. */
    public static final String CODE_INVALID = "totp_code_invalid";
    public static final String ALREADY_ENABLED = "totp_already_enabled";
    public static final String NOT_ENABLED = "totp_not_enabled";
    public static final String SETUP_REQUIRED = "totp_setup_required";
    public static final String BLOCKED = "totp_blocked";

    private TotpMessages() {
    }
}
