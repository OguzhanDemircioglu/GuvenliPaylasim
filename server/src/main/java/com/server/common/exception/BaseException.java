package com.server.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Uygulamanın bütün exception'larının atası. HTTP status ve mesaj anahtarı taşır.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;

    protected BaseException(HttpStatus status, String messageKey) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
    }
}
