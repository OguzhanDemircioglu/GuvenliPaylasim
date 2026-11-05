package com.server.common.exception;

import com.server.common.message.GlobalMessages;
import org.springframework.http.HttpStatusCode;

import java.util.regex.Pattern;

/**
 * HTTP status'tan genel mesaj anahtarına çeviri ve anahtar formatı kontrolü.
 */
public final class StatusMessages {

    private static final Pattern MESSAGE_KEY = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");

    private StatusMessages() {
    }

    public static String forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> GlobalMessages.INVALID_REQUEST;
            case 401 -> GlobalMessages.UNAUTHORIZED;
            case 403 -> GlobalMessages.ACCESS_DENIED;
            case 404 -> GlobalMessages.URL_NOT_FOUND;
            case 405 -> GlobalMessages.METHOD_NOT_ALLOWED;
            case 415 -> GlobalMessages.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? GlobalMessages.INVALID_REQUEST : GlobalMessages.UNEXPECTED_ERROR;
        };
    }

    /** Validation mesajı bir anahtar değilse (ör. message verilmeyi unutulmuşsa) frontend'e düz metin gitmesin. */
    public static boolean isMessageKey(String value) {
        return value != null && MESSAGE_KEY.matcher(value).matches();
    }
}
