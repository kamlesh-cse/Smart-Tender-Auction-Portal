package com.smartportal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class SessionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        String uri = request.getRequestURI();

        // ── Allow static resources through without session check ──────────────
        if (uri.startsWith("/css/") || uri.startsWith("/js/")
                || uri.startsWith("/images/") || uri.startsWith("/uploads/")
                || uri.equals("/favicon.ico")) {
            return true;
        }

        // ── Not logged in → redirect to login ─────────────────────────────────
        if (session == null || session.getAttribute("loggedInUser") == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        String role = (String) session.getAttribute("userRole");

        // ── Block wrong-role access ────────────────────────────────────────────
        if (uri.startsWith("/admin/") && !"ADMIN".equals(role)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (uri.startsWith("/government/") && !"GOVERNMENT".equals(role)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (uri.startsWith("/company/") && !"COMPANY".equals(role)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (uri.startsWith("/user/") && !"USER".equals(role)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        return true;
    }
}