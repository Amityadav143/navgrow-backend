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
 * A delivery zone matched by pincode prefix.
 *
 * Storing prefixes rather than every Indian pincode keeps the table small and
 * hand-maintainable while still following real postal geography (region → circle
 * → sorting district). The longest matching prefix wins.
 */
@Entity
@Table(name = "delivery_zones")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeliveryZone {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Comma-separated pincode prefixes, e.g. "70,71,72". */
    @Column(name = "pincode_prefixes", nullable = false, columnDefinition = "TEXT")
    private String pincodePrefixes;

    @Builder.Default @Column(nullable = false)
    private Boolean serviceable = true;

    @Builder.Default @Column(name = "base_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseCharge = BigDecimal.ZERO;

    /** Order value at/above which delivery is free here; null = never free. */
    @Column(name = "free_above", precision = 12, scale = 2)
    private BigDecimal freeAbove;

    @Builder.Default @Column(name = "eta_min_days", nullable = false)
    private Integer etaMinDays = 3;

    @Builder.Default @Column(name = "eta_max_days", nullable = false)
    private Integer etaMaxDays = 7;

    @Builder.Default @Column(name = "cod_available", nullable = false)
    private Boolean codAvailable = false;

    @Builder.Default @Column(name = "cod_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal codCharge = BigDecimal.ZERO;

    @Builder.Default @Column(name = "express_available", nullable = false)
    private Boolean expressAvailable = false;

    @Builder.Default @Column(name = "express_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal expressCharge = BigDecimal.ZERO;

    @Column(name = "express_eta_days")
    private Integer expressEtaDays;

    @Builder.Default @Column(nullable = false)
    private Integer priority = 0;

    @Column(length = 255)
    private String note;

    @Builder.Default @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
