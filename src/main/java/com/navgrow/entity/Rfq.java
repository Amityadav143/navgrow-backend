/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.entity;

import com.navgrow.enums.RfqStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rfq — a B2B Request-For-Quote header. Carries buyer details, line items,
 * and the admin's priced response. The core of the procurement workflow used
 * by PSU / railway / industrial buyers who purchase via quote, not cart checkout.
 */
@Entity
@Table(name = "rfqs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rfq {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-readable reference, e.g. RFQ-20260115-0007 */
    @Column(name = "rfq_number", unique = true, nullable = false)
    private String rfqNumber;

    // ── Buyer details ────────────────────────────────────────────────────────
    @Column(nullable = false) private String buyerName;
    @Column(nullable = false) private String buyerEmail;
    @Column(nullable = false) private String buyerPhone;
    private String company;
    private String gstin;
    private String deliveryCity;
    private String deliveryState;
    private String pincode;

    /** Optional link to a registered user (null for guest RFQs). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ── Line items ────────────────────────────────────────────────────────────
    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<RfqItem> items = new ArrayList<>();

    // ── Admin response ────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RfqStatus status = RfqStatus.SUBMITTED;

    @Column(name = "quoted_subtotal", precision = 12, scale = 2)
    private BigDecimal quotedSubtotal;

    @Column(name = "quoted_gst", precision = 12, scale = 2)
    private BigDecimal quotedGst;

    @Column(name = "quoted_shipping", precision = 12, scale = 2)
    private BigDecimal quotedShipping;

    @Column(name = "quoted_total", precision = 12, scale = 2)
    private BigDecimal quotedTotal;

    @Column(name = "quote_valid_until")
    private LocalDateTime quoteValidUntil;

    @Column(name = "admin_message", columnDefinition = "TEXT")
    private String adminMessage;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "payment_terms")
    private String paymentTerms;   // e.g. "100% advance", "Net 30"

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "quoted_at")
    private LocalDateTime quotedAt;

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    /** Convenience: attach an item and set the back-reference. */
    public void addItem(RfqItem item) {
        item.setRfq(this);
        this.items.add(item);
    }
}
