package com.server.common.web;

import com.server.common.message.GlobalMessages;
import com.server.common.response.ApiResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * İstek gövdesine üst sınır (Security.md S4: bellek tüketme saldırısı). En büyük istek 10 MB'lık bir belgedir;
 * Base64 ve JSON ile ~13,4 MB eder. Content-Length sınırı aşıyorsa istek okunmadan 413 döner; uzunluğu bildirilmeyen
 * (chunked) istekte okuma sınırda kesilir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {

    static final long MAX_BYTES = 16L * 1024 * 1024;

    private final ApiResponseWriter writer;

    public RequestSizeFilter(ApiResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            writer.writeError(response, HttpStatus.CONTENT_TOO_LARGE, GlobalMessages.REQUEST_TOO_LARGE);
            return;
        }
        chain.doFilter(request.getContentLengthLong() < 0 ? new Limited(request) : request, response);
    }

    /** Uzunluğu bilinmeyen gövdeyi sınırda keser. */
    private static final class Limited extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        Limited(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream in = super.getInputStream();
                stream = new ServletInputStream() {
                    private long read;

                    @Override
                    public int read() throws IOException {
                        int b = in.read();
                        if (b >= 0) {
                            count(1);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int n = in.read(buffer, offset, length);
                        if (n > 0) {
                            count(n);
                        }
                        return n;
                    }

                    private void count(long n) throws IOException {
                        read += n;
                        if (read > MAX_BYTES) {
                            throw new IOException("Request body exceeds " + MAX_BYTES + " bytes");
                        }
                    }

                    @Override
                    public boolean isFinished() {
                        return in.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return in.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        in.setReadListener(listener);
                    }
                };
            }
            return stream;
        }
    }
}
