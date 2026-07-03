/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 12, scale = 2)
    private BigDecimal mrp;

    @Builder.Default
    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Builder.Default
    @Column(name = "stock_qty")
    private Integer stockQty = 0;

    @Builder.Default
    @Column(name = "min_order_qty")
    private Integer minOrderQty = 1;

    private String badge;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    // ── Rich product detail (shown on the product detail page) ──────────────
    /** Short italic tagline shown under the product name. */
    @Column(columnDefinition = "TEXT")
    private String tagline;

    /** Longer marketing summary (falls back to description if empty). */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Warranty / guarantee text. */
    @Column(columnDefinition = "TEXT")
    private String warranty;

    /** Additional gallery image URLs, one per line. */
    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    /** Key features — one per line. */
    @Column(columnDefinition = "TEXT")
    private String features;

    /** Benefits — one per line. */
    @Column(columnDefinition = "TEXT")
    private String benefits;

    /** Applications / use-cases — one per line. */
    @Column(columnDefinition = "TEXT")
    private String applications;

    /** Specifications — one "Label: Value" per line. */
    @Column(columnDefinition = "TEXT")
    private String specifications;

    @Builder.Default
    @Column(name = "is_active")
    private boolean active = true;

    @Builder.Default
    @Column(name = "is_featured")
    private boolean featured = false;

    @Builder.Default
    @Column(precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "review_count")
    private Integer reviewCount = 0;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (stockQty == null)    stockQty = 0;
        if (minOrderQty == null) minOrderQty = 1;
        if (gstRate == null)     gstRate = new BigDecimal("18.00");
        if (rating == null)      rating = BigDecimal.ZERO;
        if (reviewCount == null) reviewCount = 0;
        if (createdAt == null)   createdAt = LocalDateTime.now();
        if (updatedAt == null)   updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
