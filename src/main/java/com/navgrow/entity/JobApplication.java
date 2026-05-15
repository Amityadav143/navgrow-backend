package com.navgrow.entity;
import com.navgrow.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "job_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobApplication {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private JobListing job;

    @Column(name = "job_title", nullable = false) private String jobTitle;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String phone;
    private String experience;
    @Column(name = "cover_note", columnDefinition = "TEXT") private String coverNote;
    @Column(name = "resume_url") private String resumeUrl;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.NEW;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
