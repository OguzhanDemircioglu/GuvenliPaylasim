package com.server.modules.user;

public final class UserMessages {

    public static final String EMAIL_REQUIRED = "user_email_required";
    public static final String EMAIL_INVALID = "user_email_invalid";
    public static final String EMAIL_EXISTS = "user_email_exists";
    public static final String FULL_NAME_REQUIRED = "user_full_name_required";
    public static final String FULL_NAME_TOO_LONG = "user_full_name_too_long";
    public static final String PASSWORD_REQUIRED = "user_password_required";
    public static final String PASSWORD_TOO_SHORT = "user_password_too_short";
    public static final String PASSWORD_TOO_LONG = "user_password_too_long";
    public static final String ROLE_REQUIRED = "user_role_required";
    public static final String ACTIVE_REQUIRED = "user_active_required";
    public static final String LAST_SUPERADMIN = "user_last_superadmin";
    public static final String NOT_MANAGEABLE = "user_not_manageable";
    public static final String ROLE_NOT_ASSIGNABLE = "user_role_not_assignable";

    private UserMessages() {
    }
}
