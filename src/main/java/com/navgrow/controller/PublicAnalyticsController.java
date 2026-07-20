/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import com.navgrow.entity.AnalyticsEvent;
import com.navgrow.repository.AnalyticsEventRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public write-only analytics ingestion — used by the frontend to report
 * real-user Web Vitals (LCP, CLS, INP, TTFB) and lightweight UX events into
 * the existing analytics_events table (V7). Read access stays under the
 * secured /admin/analytics endpoints.
 *
 * Fire-and-forget by design: invalid or over-limit events are dropped
 * silently so analytics can never disturb the user experience.
 */
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Slf4j
public class PublicAnalyticsController {

    private static final int MAX_PER_MINUTE_PER_IP = 60;
    private final AnalyticsEventRepository repo;
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    @Data
    public static class TrackReq {
        @NotBlank @Size(max = 100) String eventName;
        @Size(max = 255) String label;
        Double value;
        @Size(max = 100) String sessionId;
        @Size(max = 255) String path;
    }

    @PostMapping("/track")
    public ResponseEntity<Map<String, String>> track(@Valid @RequestBody TrackReq req,
                                                     jakarta.servlet.http.HttpServletRequest http) {
        long now = System.currentTimeMillis();
        String ip = clientIp(http);
        long[] w = windows.compute(ip, (k, v) ->
            (v == null || now - v[0] > 60_000) ? new long[]{now, 1} : new long[]{v[0], v[1] + 1});
        if (w[1] > MAX_PER_MINUTE_PER_IP) return ResponseEntity.status(429).body(Map.of("status", "rate_limited"));
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> now - e.getValue()[0] > 5 * 60_000);

        try {
            repo.save(AnalyticsEvent.builder()
                .eventName(req.getEventName().trim())
                .label(req.getLabel())
                .value(req.getValue())
                .sessionId(req.getSessionId())
                .path(req.getPath())
                .build());
        } catch (Exception e) {
            log.debug("Analytics event dropped: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
