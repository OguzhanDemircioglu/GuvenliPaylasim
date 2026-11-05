package com.server.common.response;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Controller dışındaki yerlerde (security handler'ları) ApiResponse formatında cevap yazar.
 */
@Component
@RequiredArgsConstructor
public class ApiResponseWriter {

    private final JsonMapper jsonMapper;

    public void writeError(HttpServletResponse response, HttpStatusCode status, String messageKey) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ApiResponse.error(messageKey));
    }
}
