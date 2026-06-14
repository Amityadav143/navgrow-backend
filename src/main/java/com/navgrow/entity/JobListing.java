/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;
import com.navgrow.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "job_listings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobListing {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String title;
    @Column(nullable = false) private String department;
    @Column(name = "job_type") private String jobType = "Full-time";
    @Column(nullable = false) private String location;
    private String experience;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> skills;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private JobStatus status = JobStatus.OPEN;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
