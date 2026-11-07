package com.server.common.web;

import com.server.common.response.ApiResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeFilterTest {

    private final RequestSizeFilter filter = new RequestSizeFilter(new ApiResponseWriter(JsonMapper.builder().build()));

    @Test
    void oversizedDeclaredBodyIsRejectedWithoutReading() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents") {
            @Override
            public long getContentLengthLong() {
                return RequestSizeFilter.MAX_BYTES + 1;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("request_too_large");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void normalBodyPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents");
        request.setContent(new byte[1000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void bodyWithoutLengthIsCutAtTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[(int) RequestSizeFilter.MAX_BYTES + 10]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        InputStream in = chain.getRequest().getInputStream();
        assertThatThrownBy(() -> in.readAllBytes()).isInstanceOf(IOException.class);
    }
}
