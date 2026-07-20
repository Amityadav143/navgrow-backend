/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every API request — method, path, status, duration, user and client IP —
 * so issues can be traced end-to-end in the application log file
 * (logs/navgrow-backend.log and journald on the server).
 *
 * Level policy: 5xx → ERROR, 4xx → WARN, everything else → INFO.
 * Health checks and static uploads are logged at DEBUG to keep INFO readable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        // Correlation id: appears in every log line for this request (via the
        // %X{requestId} pattern), is returned as X-Request-Id, and is echoed in
        // 500 bodies so users can quote a reference to support.
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        res.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            try {
                long ms      = System.currentTimeMillis() - start;
                int  status  = res.getStatus();
                String uri   = req.getRequestURI();
                String query = req.getQueryString();
                String line  = String.format("%s %s%s -> %d (%dms) user=%s ip=%s",
                        req.getMethod(), uri, query != null ? "?" + query : "",
                        status, ms, currentUser(), clientIp(req));

                boolean quiet = uri.contains("/actuator/") || uri.contains("/uploads/");
                if (status >= 500)      log.error(line);
                else if (status >= 400) log.warn(line);
                else if (quiet)         log.debug(line);
                else                    log.info(line);
            } catch (Exception ignored) {
                // Logging must never break the request.
            }
            MDC.remove("requestId");
        }
    }

    private String currentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
                return auth.getName();
        } catch (Exception ignored) { }
        return "-";
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
