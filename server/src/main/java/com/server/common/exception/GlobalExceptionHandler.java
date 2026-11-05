package com.server.common.exception;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Collection;
import java.util.Objects;

/**
 * Controller'dan çıkan bütün hataları ApiResponse formatına çevirir.
 * Stacktrace, SQL ya da sınıf adı response'a asla çıkmaz.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBase(BaseException ex) {
        return respond(ex.getStatus(), ex.getMessageKey());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        return respond(HttpStatus.BAD_REQUEST, firstMessage(ex.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameters(HandlerMethodValidationException ex) {
        return respond(HttpStatus.BAD_REQUEST, firstMessage(ex.getAllErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(StatusMessages::isMessageKey)
                .findFirst()
                .orElse(GlobalMessages.INVALID_REQUEST);
        return respond(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, GlobalMessages.INVALID_REQUEST);
    }

    @ExceptionHandler({TypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, GlobalMessages.INVALID_PARAMETER);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, GlobalMessages.UNAUTHORIZED);
    }

    // @PreAuthorize reddi controller içinde fırlar; Exception handler'ına düşüp 500 olmasın diye açıkça yakalanır.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, GlobalMessages.ACCESS_DENIED);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return respond(HttpStatus.CONFLICT, GlobalMessages.RECORD_MODIFIED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, GlobalMessages.RECORD_CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        // Spring MVC exception'ları (404 NoResourceFound, 405, 415 ...) kendi status'unu taşır.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return respond(status, StatusMessages.forStatus(status));
        }
        log.error("Unexpected error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, GlobalMessages.UNEXPECTED_ERROR);
    }

    private static String firstMessage(Collection<? extends MessageSourceResolvable> errors) {
        return errors.stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .filter(StatusMessages::isMessageKey)
                .findFirst()
                .orElse(GlobalMessages.INVALID_REQUEST);
    }

    /** Bütün 4xx cevapları status ve mesaj anahtarıyla INFO seviyesinde loglanır (traceId MDC'den log satırına girer). */
    private static ResponseEntity<ApiResponse<Void>> respond(HttpStatusCode status, String messageKey) {
        if (status.is4xxClientError()) {
            log.info("{} {}", status.value(), messageKey);
        }
        return ResponseEntity.status(status).body(ApiResponse.error(messageKey));
    }
}
