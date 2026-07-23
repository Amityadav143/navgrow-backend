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

/**
 * Default HSN/SAC code and GST rate for a product category.
 *
 * GST is not one number across a catalogue — safety garments sit at 12% while
 * tooling and instruments sit at 18% — so admins need to set the rate per
 * category rather than per product every time. A product that carries its own
 * HSN/rate always wins; this only fills the gaps.
 */
@Entity
@Table(name = "category_tax_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CategoryTaxRule {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String category;

    @Column(name = "hsn_code", length = 12)
    private String hsnCode;

    @Builder.Default
    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Column(length = 255)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (gstRate == null)   gstRate   = new BigDecimal("18.00");
        if (active == null)    active    = true;
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
