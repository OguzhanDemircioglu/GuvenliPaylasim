package com.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Kayıt bu arada değişmiş ya da çakışma var (409).
 */
public class ConflictException extends BaseException {

    public ConflictException(String messageKey) {
        super(HttpStatus.CONFLICT, messageKey);
    }
}
