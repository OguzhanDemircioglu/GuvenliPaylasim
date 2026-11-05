package com.server.security;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Rolün yetkisi yok: 403, ApiResponse formatında.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiResponseWriter writer;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        log.info("403 {} {} {}", GlobalMessages.ACCESS_DENIED, request.getMethod(), request.getRequestURI());
        writer.writeError(response, HttpStatus.FORBIDDEN, GlobalMessages.ACCESS_DENIED);
    }
}
