package com.navgrow.entity;
import com.navgrow.enums.QuoteStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "quote_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuoteRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String name;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String phone;
    private String company;
    @Column(name = "service_type", nullable = false) private String serviceType;
    private String scope;
    private String duration;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> addons;

    @Column(name = "est_low",  precision = 12, scale = 2) private BigDecimal estLow;
    @Column(name = "est_high", precision = 12, scale = 2) private BigDecimal estHigh;
    @Column(columnDefinition = "TEXT") private String notes;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.NEW;

    @Column(name = "quoted_amount", precision = 12, scale = 2) private BigDecimal quotedAmount;
    @Column(name = "assigned_to") private String assignedTo;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
