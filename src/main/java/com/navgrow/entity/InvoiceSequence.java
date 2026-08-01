/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** One row per financial-year invoice series; holds the last number issued. */
@Entity
@Table(name = "invoice_sequence")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceSequence {
    @Id
    @Column(name = "fin_year", length = 9)
    private String finYear;            // e.g. "2025-26"

    @Column(name = "last_seq", nullable = false)
    private Long lastSeq = 0L;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
