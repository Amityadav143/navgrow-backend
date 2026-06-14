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

@Entity @Table(name = "contact_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactMessage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private String name;
    @Column(nullable = false) private String email;
    private String phone;
    private String company;
    @Column(nullable = false) private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;

    @Column(name = "is_read") private boolean read = false;
    @Column(name = "replied_at") private LocalDateTime repliedAt;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
