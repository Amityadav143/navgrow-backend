/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLog — a record of a mutating API request, for administrative traceability.
 * Captures who (userEmail), what (method + endpoint), the outcome (statusCode),
 * where (ipAddress), and how long (durationMs).
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_created", columnList = "created_at"),
    @Index(name = "idx_audit_method", columnList = "method")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 10)
    private String method;

    @Column(length = 300)
    private String endpoint;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "user_email", length = 160)
    private String userEmail;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Builder.Default
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    // Alias so the API can expose `timestamp` to match the frontend.
    @Transient
    public LocalDateTime getTimestamp() { return createdAt; }
}
