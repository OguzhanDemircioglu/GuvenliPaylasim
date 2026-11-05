package com.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Brute-force koruması nedeniyle login geçici olarak engellendi (429).
 */
public class LoginBlockedException extends BaseException {

    public LoginBlockedException(String messageKey) {
        super(HttpStatus.TOO_MANY_REQUESTS, messageKey);
    }
}
