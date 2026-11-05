package com.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Kimlik doğrulanamadı (401). Örnek: yanlış e-posta/şifre, süresi dolmuş oturum.
 */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException(String messageKey) {
        super(HttpStatus.UNAUTHORIZED, messageKey);
    }
}
