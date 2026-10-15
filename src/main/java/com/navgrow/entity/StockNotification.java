/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A "notify me when back in stock" request. When a visitor hits an out-of-stock
 * product we capture their email here instead of losing the lead. The admin can
 * see pent-up demand per product and notify everyone once it's restocked.
 */
@Entity
@Table(name = "stock_notifications",
    uniqueConstraints = @UniqueConstraint(name = "uq_stock_notif_email_slug", columnNames = {"email", "product_slug"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockNotification {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String email;
    private String name;

    // Product reference — slug is the stable public identifier; name is captured
    // for display so the admin list reads well even if a product is renamed.
    @Column(name = "product_slug", nullable = false) private String productSlug;
    @Column(name = "product_name") private String productName;
    @Column(name = "product_id") private UUID productId;

    @Builder.Default
    @Column(nullable = false) private boolean notified = false;

    @Builder.Default
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "notified_at") private LocalDateTime notifiedAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
