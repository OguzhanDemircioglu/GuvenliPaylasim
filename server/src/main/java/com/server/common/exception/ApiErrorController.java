package com.server.common.exception;

import com.server.common.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filtre seviyesinde oluşan ve Spring'in /error endpoint'ine düşen hataları da ApiResponse formatında döner.
 * Spring Boot'un varsayılan hata cevabının yerini alır.
 * <p>
 * Controller'a ulaşan istekler {@link GlobalExceptionHandler}'da cevaplanır; buraya sadece onun göremediği hatalar gelir:
 * <ul>
 *   <li>Security firewall'unun reddettiği istekler (ör. URL'de {@code ;}, {@code %2e}): {@code sendError(400)} → {@code invalid_request}</li>
 *   <li>Filtrelerde fırlayan beklenmedik exception'lar → 500 {@code unexpected_error}</li>
 *   <li>Container'ın kendi {@code sendError} çağrıları (status neyse o)</li>
 * </ul>
 * Status ile mesaj anahtarı eşlemesi {@link StatusMessages#forStatus} içindedir. Error dispatch'i de {@code TraceIdFilter}'dan
 * geçtiği için cevaptaki traceId, ilk isteğinkiyle aynıdır.
 * <p>
 * 4xx cevaplar {@code INFO}, 5xx cevaplar (varsa exception'ın stacktrace'iyle) {@code ERROR} seviyesinde loglanır.
 */
@Slf4j
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                ? HttpStatus.resolve(code)
                : null;
        if (status == null || !status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String messageKey = StatusMessages.forStatus(status);
        // request.getRequestURI() burada "/error"dur; asıl adres error attribute'unda.
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (status.is4xxClientError()) {
            log.info("{} {} {} {}", status.value(), messageKey, request.getMethod(), uri);
        } else {
            log.error("{} {} {} {}", status.value(), messageKey, request.getMethod(), uri,
                    request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        }
        return ResponseEntity.status(status).body(ApiResponse.error(messageKey));
    }
}
