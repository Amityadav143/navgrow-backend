/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * SiteSetting — a simple key→JSON store for global site configuration
 * (banners, popups, shop settings, maintenance mode). One row per key.
 * The admin dashboard reads/writes the "global" key holding the full JSON.
 */
@Entity
@Table(name = "site_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SiteSetting {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist @PreUpdate
    public void touch() { this.updatedAt = LocalDateTime.now(); }
}
