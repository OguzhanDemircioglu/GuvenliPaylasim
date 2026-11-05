package com.server.security;

/**
 * Access token'a konan özel claim adları. sub = kullanıcı id'si.
 */
public final class JwtClaims {

    public static final String EMAIL = "email";
    public static final String ROLE = "role";
    public static final String SESSION_ID = "sid";

    private JwtClaims() {
    }
}
