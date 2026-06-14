/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;
import com.navgrow.enums.TenderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "tenders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tender {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ref_number", unique = true, nullable = false) private String refNumber;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "value_min", precision = 12, scale = 2) private BigDecimal valueMin;
    @Column(name = "value_max", precision = 12, scale = 2) private BigDecimal valueMax;
    @Column(nullable = false) private LocalDate deadline;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TenderStatus status = TenderStatus.OPEN;

    @Column(name = "is_featured") private boolean featured = false;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
