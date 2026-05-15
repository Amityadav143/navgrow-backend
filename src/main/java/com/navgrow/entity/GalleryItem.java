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
    @Column(name = "sort_order") private int sortOrder = 0;
    @Column(name = "is_active") private boolean active = true;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
