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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiting for authentication endpoints.
 *
 * Login, registration, OTP-send and password-reset previously had no
 * throttling at all (only the chatbot did), leaving them open to
 * credential brute-forcing and SMS-cost abuse. Limits are per client IP
 * per endpoint; sized generously so real users never notice.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4) // runs just before RequestLoggingFilter
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** endpoint suffix (after the /api context) → [maxRequests, windowMs] */
    private static final Map<String, long[]> LIMITS = Map.of(
        "/auth/login",           new long[]{10, 60_000},   // 10 / minute
        "/auth/login-with-phone",new long[]{10, 60_000},
        "/auth/register",        new long[]{ 5, 60_000},   //  5 / minute
        "/auth/send-otp",        new long[]{ 3, 60_000},   //  3 / minute (SMS cost)
        "/auth/verify-otp",      new long[]{10, 60_000},
        "/auth/forgot-password", new long[]{ 3, 60_000},
        "/catalogue/leads",      new long[]{ 5, 60_000}    //  5 / minute (spam + email cost)
    );
    private static final int MAX_TRACKED_KEYS = 10_000;

    /** key = ip + "|" + path → [windowStartMs, count] */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();
        String matched = null;
        for (String suffix : LIMITS.keySet()) {
            if (path.endsWith(suffix)) { matched = suffix; break; }
        }
        if (matched == null || !"POST".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        long[] limit = LIMITS.get(matched);
        String key = clientIp(req) + "|" + matched;
        long now = System.currentTimeMillis();

        // Opportunistic cleanup so the map can never grow unbounded.
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue()[0] > 10 * 60_000);
        }

        long[] w = windows.compute(key, (k, v) -> {
            if (v == null || now - v[0] > limit[1]) return new long[]{now, 1};
            v[1]++;
            return v;
        });

        if (w[1] > limit[0]) {
            log.warn("Rate limit hit: {} {} from ip={} ({} req in window)",
                     req.getMethod(), path, clientIp(req), w[1]);
            res.setStatus(429);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setHeader("Retry-After", "60");
            res.getWriter().write("{\"message\":\"Too many attempts. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
