package com.smartportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = valueOrGenerate(request.getHeader("X-Request-ID"));
        String traceId = valueOrGenerate(request.getHeader("X-Trace-ID"));
        Object user = request.getSession(false) != null
                ? request.getSession(false).getAttribute("loggedInUser") : null;
        try {
            MDC.put("requestId", requestId);
            MDC.put("traceId", traceId);
            MDC.put("userId", user != null ? user.toString() : "anonymous");
            response.setHeader("X-Request-ID", requestId);
            response.setHeader("X-Trace-ID", traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String valueOrGenerate(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}")
                ? value : UUID.randomUUID().toString();
    }
}
