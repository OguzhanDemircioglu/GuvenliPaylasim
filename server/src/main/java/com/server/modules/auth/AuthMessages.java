package com.server.modules.auth;

public final class AuthMessages {

    public static final String PHONE_REQUIRED = "auth_phone_required";
    public static final String PHONE_INVALID = "auth_phone_invalid";
    public static final String PASSWORD_REQUIRED = "auth_password_required";
    public static final String NEW_PASSWORD_TOO_SHORT = "auth_new_password_too_short";
    public static final String NEW_PASSWORD_TOO_LONG = "auth_new_password_too_long";
    public static final String INVALID_CREDENTIALS = "auth_invalid_credentials";
    public static final String SESSION_EXPIRED = "auth_session_expired";
    public static final String LOGIN_BLOCKED = "auth_login_blocked";
    public static final String CURRENT_PASSWORD_INCORRECT = "auth_current_password_incorrect";
    public static final String KEYS_REQUIRED = "auth_keys_required";

    private AuthMessages() {
    }
}
