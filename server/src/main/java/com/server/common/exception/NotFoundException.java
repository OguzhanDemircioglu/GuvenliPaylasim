package com.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Kayıt bulunamadı (404).
 */
public class NotFoundException extends BaseException {

    public NotFoundException(String messageKey) {
        super(HttpStatus.NOT_FOUND, messageKey);
    }
}
