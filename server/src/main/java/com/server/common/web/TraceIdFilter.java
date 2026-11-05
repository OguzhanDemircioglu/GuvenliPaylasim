package com.server.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Her isteğe bir traceId verir; MDC'ye (loglar için) ve X-Trace-Id başlığına yazar.
 * Security filtrelerinden önce çalışır, böylece 401/403 cevaplarında da traceId bulunur.
 * /error yönlendirmesinde aynı traceId tekrar kullanılır.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    private static final String REQUEST_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = (String) request.getAttribute(REQUEST_ATTRIBUTE);
        if (traceId == null) {
            traceId = newTraceId();
            request.setAttribute(REQUEST_ATTRIBUTE, traceId);
            response.setHeader(HEADER, traceId);
        }
        MDC.put(MDC_KEY, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    private static String newTraceId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
