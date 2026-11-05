package com.server.security;

import com.server.common.exception.ApiErrorController;
import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponse;
import com.server.common.response.ApiResponseWriter;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller dışındaki çıkış noktaları (401/403 handler'ları ve /error) da aynı formatı dönmeli.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityErrorResponsesTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ApiResponseWriter writer = new ApiResponseWriter(jsonMapper);

    @Test
    void entryPointWrites401InApiFormat() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RestAuthenticationEntryPoint(writer)
                .commence(new MockHttpServletRequest(), response, new BadCredentialsException("bad token"));

        assertApiError(response, 401, GlobalMessages.UNAUTHORIZED);
    }

    @Test
    void accessDeniedHandlerWrites403InApiFormat() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RestAccessDeniedHandler(writer)
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertApiError(response, 403, GlobalMessages.ACCESS_DENIED);
    }

    @Test
    void errorControllerMapsStatusToMessageKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);

        ResponseEntity<ApiResponse<Void>> response = new ApiErrorController().error(request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo(GlobalMessages.URL_NOT_FOUND);
    }

    @Test
    void errorControllerFallsBackTo500() {
        ResponseEntity<ApiResponse<Void>> response = new ApiErrorController().error(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo(GlobalMessages.UNEXPECTED_ERROR);
    }

    @Test
    void errorControllerLogs500WithStacktrace(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/categories");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("filter failed"));

        new ApiErrorController().error(request);

        assertThat(output)
                .contains("ERROR")
                .contains("500 " + GlobalMessages.UNEXPECTED_ERROR + " GET /api/categories")
                .contains("java.lang.IllegalStateException: filter failed");
    }

    private void assertApiError(MockHttpServletResponse response, int status, String message) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("success", "message", "data", "traceId");
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asString()).isEqualTo(message);
        assertThat(body.get("data").isNull()).isTrue();
    }
}
