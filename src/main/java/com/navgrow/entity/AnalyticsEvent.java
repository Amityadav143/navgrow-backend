/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AnalyticsEvent — a single product/funnel event (e.g. product_view, add_to_cart,
 * checkout_start, order_placed, rfq_submit, rfq_accept). Lightweight and
 * privacy-respecting: no PII, just an event name, an optional label/value, and an
 * anonymous session id so funnels can be reconstructed without identifying users.
 */
@Entity
@Table(name = "analytics_events", indexes = {
    @Index(name = "idx_ae_event", columnList = "event_name"),
    @Index(name = "idx_ae_created", columnList = "created_at"),
    @Index(name = "idx_ae_session", columnList = "session_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** e.g. "product_view", "add_to_cart", "checkout_start", "order_placed", "rfq_submit" */
    @Column(name = "event_name", nullable = false, length = 60)
    private String eventName;

    /** optional context, e.g. a product slug, category, or page path */
    @Column(name = "label", length = 200)
    private String label;

    /** optional numeric value, e.g. cart total or item count */
    @Column(name = "value")
    private Double value;

    /** anonymous client session id (no PII) */
    @Column(name = "session_id", length = 80)
    private String sessionId;

    /** optional page path where the event fired */
    @Column(name = "path", length = 200)
    private String path;

    @Builder.Default
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
