package com.server.common.response;

import com.server.common.message.GlobalMessages;
import com.server.common.web.TraceIdFilter;

/**
 * Frontend'e giden tek response formatı. DEĞİŞMEZ: alan eklenmez, çıkarılmaz, adı değiştirilmez.
 *
 * @param success işlem başarılıysa true
 * @param message her zaman snake_case bir mesaj anahtarı
 * @param data    başarılı cevapta veri; yoksa null
 * @param traceId isteğin izleme kodu (loglarda da aynı değer geçer)
 */
public record ApiResponse<T>(boolean success, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return success(GlobalMessages.SUCCESS, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, TraceIdFilter.current());
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, TraceIdFilter.current());
    }
}
