/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;

import com.navgrow.enums.LeadStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A lead captured when a visitor downloads the company catalogue. We collect
 * name, mobile, email and their requirement before serving the file, so the
 * sales team has a warm contact to follow up. Visible in Admin → Catalogue Leads.
 */
@Entity
@Table(name = "catalogue_leads")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CatalogueLead {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String name;
    @Column(nullable = false) private String mobile;
    @Column(nullable = false) private String email;

    /** Free-text description of what the visitor is looking for. */
    @Column(columnDefinition = "TEXT") private String requirement;

    /** Optional context. */
    private String company;
    private String city;

    /** Which document was requested (future-proofing for multiple catalogues). */
    @Column(name = "catalogue_key") private String catalogueKey;

    /** Light analytics / audit context. */
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "user_agent", length = 512) private String userAgent;
    private String source;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "lead_status")
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "assigned_to") private String assignedTo;
    @Column(columnDefinition = "TEXT") private String adminNotes;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
