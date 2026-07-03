/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.controller;

import com.navgrow.entity.AnalyticsEvent;
import com.navgrow.repository.AnalyticsEventRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AnalyticsEventController
 *  POST /analytics/events       → public, records a single funnel event (no PII)
 *  GET  /admin/analytics/funnel → admin, returns conversion-funnel summary
 */
@RestController
@RequiredArgsConstructor
public class AnalyticsEventController {

    private final AnalyticsEventRepository repo;

    // ── Public: record an event ───────────────────────────────────────────────
    @Data
    public static class EventReq {
        @NotBlank @Size(max = 60)  private String event;
        @Size(max = 200)           private String label;
        private Double value;
        @Size(max = 80)            private String sessionId;
        @Size(max = 200)           private String path;
    }

    @PostMapping("/analytics/events")
    public ResponseEntity<Void> record(@RequestBody EventReq req) {
        // Defensive: ignore anything without an event name; never throw to the client.
        if (req == null || req.getEvent() == null || req.getEvent().isBlank()) {
            return ResponseEntity.noContent().build();
        }
        try {
            repo.save(AnalyticsEvent.builder()
                .eventName(req.getEvent().trim())
                .label(trimOrNull(req.getLabel(), 200))
                .value(req.getValue())
                .sessionId(trimOrNull(req.getSessionId(), 80))
                .path(trimOrNull(req.getPath(), 200))
                .build());
        } catch (Exception ignored) {
            // Analytics must never break the user experience.
        }
        return ResponseEntity.noContent().build();
    }

    // ── Admin: funnel summary ─────────────────────────────────────────────────
    @GetMapping("/admin/analytics/funnel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> funnel(
            @RequestParam(defaultValue = "30") int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 365)));

        // event -> count
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : repo.countByEventSince(since)) {
            counts.put((String) row[0], (Long) row[1]);
        }

        // Storefront funnel by distinct sessions (so each visitor counts once per stage)
        String[] shopStages = {"product_view", "add_to_cart", "checkout_start", "order_placed"};
        List<Map<String, Object>> shopFunnel = new ArrayList<>();
        long prev = -1;
        for (String stage : shopStages) {
            long sessions = repo.countDistinctSessions(stage, since);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", stage);
            m.put("sessions", sessions);
            m.put("dropFromPrev", prev < 0 ? null : (prev == 0 ? 0 : Math.round((prev - sessions) * 1000.0 / prev) / 10.0));
            shopFunnel.add(m);
            prev = sessions;
        }

        // RFQ funnel
        String[] rfqStages = {"rfq_start", "rfq_submit", "rfq_accept"};
        List<Map<String, Object>> rfqFunnel = new ArrayList<>();
        for (String stage : rfqStages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", stage);
            m.put("sessions", repo.countDistinctSessions(stage, since));
            rfqFunnel.add(m);
        }

        // Top viewed products
        List<Map<String, Object>> topProducts = new ArrayList<>();
        List<Object[]> labels = repo.topLabels("product_view", since);
        for (int i = 0; i < Math.min(labels.size(), 8); i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", labels.get(i)[0]);
            m.put("views", labels.get(i)[1]);
            topProducts.add(m);
        }

        // Conversion rate: order_placed sessions / product_view sessions
        long viewers = repo.countDistinctSessions("product_view", since);
        long buyers  = repo.countDistinctSessions("order_placed", since);
        double convRate = viewers == 0 ? 0 : Math.round(buyers * 1000.0 / viewers) / 10.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("totalEvents", repo.countByCreatedAtAfter(since));
        out.put("eventCounts", counts);
        out.put("shopFunnel", shopFunnel);
        out.put("rfqFunnel", rfqFunnel);
        out.put("topProducts", topProducts);
        out.put("conversionRate", convRate);
        return ResponseEntity.ok(out);
    }

    private static String trimOrNull(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
