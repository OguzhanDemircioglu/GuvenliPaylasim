package com.server.security;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Token yok ya da geçersiz: 401, ApiResponse formatında. (Security hataları ControllerAdvice'a ulaşmaz.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiResponseWriter writer;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        log.info("401 {} {} {}", GlobalMessages.UNAUTHORIZED, request.getMethod(), request.getRequestURI());
        writer.writeError(response, HttpStatus.UNAUTHORIZED, GlobalMessages.UNAUTHORIZED);
    }
}
