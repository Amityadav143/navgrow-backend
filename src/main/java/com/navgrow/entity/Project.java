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
    @Column(name = "is_featured") private boolean featured = false;
    @Column(name = "sort_order") private int sortOrder = 0;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
