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

@Entity @Table(name = "gallery_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GalleryItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String title;
    @Column(nullable = false) private String category;
    private String location;
    private String year;
    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT") private String imageUrl;
    @Column(name = "alt_text") private String altText;
    @Builder.Default @Column(name = "sort_order") private int sortOrder = 0;
    @Builder.Default @Column(name = "is_active") private boolean active = true;
    @Builder.Default @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
