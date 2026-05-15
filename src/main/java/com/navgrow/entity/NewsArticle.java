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
    @Column(name = "author_name") private String authorName = "Navgrow Team";

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NewsStatus status = NewsStatus.DRAFT;

    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "view_count") private int viewCount = 0;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
