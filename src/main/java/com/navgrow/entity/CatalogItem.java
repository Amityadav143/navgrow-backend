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
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin-managed taxonomy: product categories, services, gallery categories,
 * news categories. One flexible table so new admin-editable lists don't each
 * need a migration.
 */
@Entity @Table(name = "catalog_items",
    uniqueConstraints = @UniqueConstraint(name = "uq_catalog_type_slug", columnNames = {"item_type", "slug"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CatalogItem {

    public static final String TYPE_PRODUCT_CATEGORY = "PRODUCT_CATEGORY";
    public static final String TYPE_SERVICE          = "SERVICE";
    public static final String TYPE_GALLERY_CATEGORY = "GALLERY_CATEGORY";
    public static final String TYPE_NEWS_CATEGORY    = "NEWS_CATEGORY";

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "item_type", nullable = false, length = 40) private String itemType;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, length = 160) private String slug;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(length = 60) private String icon;
    @Column(name = "image_url", columnDefinition = "TEXT") private String imageUrl;
    @Builder.Default @Column(name = "sort_order", nullable = false) private int sortOrder = 0;
    @Builder.Default @Column(name = "is_active", nullable = false) private boolean active = true;

    @Builder.Default @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
