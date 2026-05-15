package com.navgrow.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "newsletter_subscribers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NewsletterSubscriber {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false) private String email;
    private String name;
    @Column(name = "is_active") private boolean active = true;
    @Column(name = "subscribed_at", updatable = false) private LocalDateTime subscribedAt = LocalDateTime.now();
    @Column(name = "unsubscribed_at") private LocalDateTime unsubscribedAt;
}
