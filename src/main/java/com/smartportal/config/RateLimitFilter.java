package com.smartportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean trustForwardedFor;

    public RateLimitFilter(
            @Value("${app.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (limit != null) {
            String forwarded = trustForwardedFor ? request.getHeader("X-Forwarded-For") : null;
            String ip = forwarded != null && !forwarded.isBlank()
                    ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
            String key = ip + ":" + request.getRequestURI();
            Window window = windows.compute(key, (ignored, current) ->
                    current == null || current.expiresAt < Instant.now().getEpochSecond()
                            ? new Window(1, Instant.now().getEpochSecond() + limit.seconds)
                            : new Window(current.count + 1, current.expiresAt));
            if (window.count > limit.requests) {
                response.setStatus(429);
                response.setHeader("Retry-After",
                        String.valueOf(Math.max(1, window.expiresAt - Instant.now().getEpochSecond())));
                response.getWriter().write("Too many requests");
                return;
            }
            if (windows.size() > 10_000) {
                long now = Instant.now().getEpochSecond();
                windows.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
            }
        }
        filterChain.doFilter(request, response);
    }

    private Limit limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return null;
        String uri = request.getRequestURI();
        if ("/login".equals(uri)) return new Limit(10, 60);
        if ("/forgot-password".equals(uri) || "/verify-otp".equals(uri)) return new Limit(5, 300);
        if (uri.contains("/bid/") || uri.contains("/tender/submit/")) return new Limit(30, 60);
        return null;
    }

    private record Limit(int requests, long seconds) {}
    private record Window(int count, long expiresAt) {}
}
