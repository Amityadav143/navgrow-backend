/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;
import com.navgrow.enums.NewsStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "news_articles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NewsArticle {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String title;
    @Column(unique = true, nullable = false) private String slug;
    @Column(columnDefinition = "TEXT") private String excerpt;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    private String category;
    @Column(name = "image_url", columnDefinition = "TEXT") private String imageUrl;
    /** Additional gallery image URLs, one per line. */
    @Column(name = "image_urls", columnDefinition = "TEXT") private String imageUrls;
    @Column(name = "author_name") private String authorName = "Navgrow Team";

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default
    private NewsStatus status = NewsStatus.DRAFT;

    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Builder.Default @Column(name = "view_count") private int viewCount = 0;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    @Builder.Default @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @PrePersist public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
