/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/** A single line in an RFQ — product reference, requested qty, and (later) quoted unit price. */
@Entity
@Table(name = "rfq_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RfqItem {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_id", nullable = false)
    @JsonIgnore
    private Rfq rfq;

    /** Product reference — UUID for catalogue products, or null for custom/free-text lines. */
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    /** Buyer's free-text spec / requirement for this line. */
    @Column(columnDefinition = "TEXT")
    private String specification;

    /** Admin-quoted unit price (null until quoted). */
    @Column(name = "quoted_unit_price", precision = 12, scale = 2)
    private BigDecimal quotedUnitPrice;

    /** Admin-quoted line total (unit × qty, post any line discount). */
    @Column(name = "line_total", precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "gst_rate")
    private Integer gstRate;
}
