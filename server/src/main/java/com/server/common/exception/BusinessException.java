package com.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * İş kuralı ihlali (422). Örnek: throw new BusinessException(CategoryMessages.NAME_EXISTS).
 */
public class BusinessException extends BaseException {

    public BusinessException(String messageKey) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, messageKey);
    }
}
