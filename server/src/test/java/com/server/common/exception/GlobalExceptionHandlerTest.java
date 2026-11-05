package com.server.common.exception;

import com.server.common.message.GlobalMessages;
import com.server.common.web.TraceIdFilter;
import com.server.modules.category.CategoryMessages;
import com.server.modules.category.dto.CategoryCreateRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrat testi: her hata durumunda response tam olarak {success, message, data, traceId} olmalı.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void validationErrorReturnsFirstFieldKey() throws Exception {
        expectError(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"),
                400, CategoryMessages.NAME_REQUIRED);
    }

    @Test
    void malformedJsonReturnsInvalidRequest() throws Exception {
        expectError(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{bad json"),
                400, GlobalMessages.INVALID_REQUEST);
    }

    @Test
    void wrongParameterTypeReturnsInvalidParameter() throws Exception {
        expectError(get("/test/typed").param("id", "abc"), 400, GlobalMessages.INVALID_PARAMETER);
    }

    @Test
    void missingParameterReturnsInvalidParameter() throws Exception {
        expectError(get("/test/typed"), 400, GlobalMessages.INVALID_PARAMETER);
    }

    @Test
    void unauthorizedExceptionReturns401() throws Exception {
        expectError(get("/test/throw").param("type", "unauthorized"), 401, "auth_invalid_credentials");
    }

    @Test
    void accessDeniedReturns403() throws Exception {
        expectError(get("/test/throw").param("type", "denied"), 403, GlobalMessages.ACCESS_DENIED);
    }

    @Test
    void notFoundExceptionReturns404() throws Exception {
        expectError(get("/test/throw").param("type", "notFound"), 404, GlobalMessages.RECORD_NOT_FOUND);
    }

    @Test
    void unknownUrlReturns404() throws Exception {
        expectError(get("/test/does-not-exist"), 404, GlobalMessages.URL_NOT_FOUND);
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        expectError(put("/test/typed"), 405, GlobalMessages.METHOD_NOT_ALLOWED);
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        expectError(post("/test/validated").contentType(MediaType.TEXT_PLAIN).content("x"),
                415, GlobalMessages.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void conflictExceptionReturns409() throws Exception {
        expectError(get("/test/throw").param("type", "conflict"), 409, GlobalMessages.RECORD_MODIFIED);
    }

    @Test
    void optimisticLockReturns409() throws Exception {
        expectError(get("/test/throw").param("type", "optimisticLock"), 409, GlobalMessages.RECORD_MODIFIED);
    }

    @Test
    void dataIntegrityViolationReturns409() throws Exception {
        expectError(get("/test/throw").param("type", "integrity"), 409, GlobalMessages.RECORD_CONFLICT);
    }

    @Test
    void businessExceptionReturns422() throws Exception {
        expectError(get("/test/throw").param("type", "business"), 422, CategoryMessages.NAME_EXISTS);
    }

    @Test
    void loginBlockedReturns429() throws Exception {
        expectError(get("/test/throw").param("type", "blocked"), 429, "auth_login_blocked");
    }

    @Test
    void unexpectedErrorReturns500WithoutDetails() throws Exception {
        expectError(get("/test/throw").param("type", "npe"), 500, GlobalMessages.UNEXPECTED_ERROR);
    }

    private void expectError(MockHttpServletRequestBuilder request, int status, String message) throws Exception {
        ResultActions result = mockMvc.perform(request);
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$", hasKey("data")))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{16}")))
                .andExpect(header().exists(TraceIdFilter.HEADER));
    }

    @RestController
    static class FailingController {

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody CategoryCreateRequest request) {
        }

        @GetMapping("/test/typed")
        void typed(@RequestParam Long id) {
        }

        @GetMapping("/test/throw")
        void fail(@RequestParam String type) {
            switch (type) {
                case "unauthorized" -> throw new UnauthorizedException("auth_invalid_credentials");
                case "denied" -> throw new AccessDeniedException("denied");
                case "notFound" -> throw new NotFoundException(GlobalMessages.RECORD_NOT_FOUND);
                case "conflict" -> throw new ConflictException(GlobalMessages.RECORD_MODIFIED);
                case "optimisticLock" -> throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
                case "integrity" -> throw new DataIntegrityViolationException("duplicate key value");
                case "business" -> throw new BusinessException(CategoryMessages.NAME_EXISTS);
                case "blocked" -> throw new LoginBlockedException("auth_login_blocked");
                default -> throw new NullPointerException("boom");
            }
        }
    }
}
