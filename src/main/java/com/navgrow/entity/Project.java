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

@Entity @Table(name = "projects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String title;
    @Column(unique = true, nullable = false) private String slug;
    @Column(nullable = false) private String category;
    private String client;
    private String location;
    private String year;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "image_url", columnDefinition = "TEXT") private String imageUrl;
    @Builder.Default @Column(name = "is_featured") private boolean featured = false;
    @Builder.Default @Column(name = "sort_order") private int sortOrder = 0;
    @Builder.Default @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
