/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.security;

import com.navgrow.entity.AuditLog;
import com.navgrow.repository.AuditLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * AuditLogFilter — records mutating API requests (POST/PUT/PATCH/DELETE) for
 * administrative traceability. Read requests and the audit endpoint itself are
 * skipped to keep the log meaningful and avoid recursion. Failures never affect
 * the request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AuditLogRepository repo;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            try {
                String method = req.getMethod();
                String uri = req.getRequestURI();
                boolean skip = !MUTATING.contains(method)
                        || uri.contains("/audit-logs")
                        || uri.contains("/analytics/events");
                if (!skip) {
                    String user = null;
                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                        user = auth.getName();
                    }
                    repo.save(AuditLog.builder()
                        .method(method)
                        .endpoint(uri.length() > 300 ? uri.substring(0, 300) : uri)
                        .statusCode(res.getStatus())
                        .userEmail(user)
                        .ipAddress(clientIp(req))
                        .durationMs(System.currentTimeMillis() - start)
                        .build());
                }
            } catch (Exception ignored) {
                // Auditing must never break the request.
            }
        }
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
