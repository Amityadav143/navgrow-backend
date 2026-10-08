/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;

import com.navgrow.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_role")
    @Builder.Default
    private UserRole role = UserRole.USER;

    /**
     * Custom per-user admin permissions, stored as a comma-separated list of
     * {@link com.navgrow.enums.Permission} names (e.g. "ORDERS,PRODUCTS,NEWS").
     * Null/empty means "no custom grants" — access then follows the base role.
     * A SUPER_ADMIN assigns these to give a user access to specific admin areas.
     */
    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    private String company;

    // ── Address fields (added v1.0) ──────────────────────────────────────────
    @Column(name = "locality")
    private String locality;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "pincode")
    private String pincode;

    // ── Profile extras ────────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * The effective set of admin permissions for this user, exposed to the client
     * as a JSON array. SUPER_ADMIN and ADMIN implicitly have ALL permissions;
     * MANAGER/EDITOR/USER get only what was explicitly granted via {@link #permissions}.
     * Unknown/legacy tokens are ignored so the app never breaks on bad data.
     */
    @Transient
    @JsonProperty("effectivePermissions")
    public java.util.Set<String> getEffectivePermissions() {
        if (role == com.navgrow.enums.UserRole.SUPER_ADMIN || role == com.navgrow.enums.UserRole.ADMIN) {
            java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
            for (com.navgrow.enums.Permission p : com.navgrow.enums.Permission.values()) out.add(p.name());
            return out;
        }
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (permissions != null && !permissions.isBlank()) {
            for (String tok : permissions.split(",")) {
                String t = tok.trim().toUpperCase();
                if (t.isEmpty()) continue;
                try { out.add(com.navgrow.enums.Permission.valueOf(t).name()); }
                catch (IllegalArgumentException ignored) { /* skip unknown */ }
            }
        }
        return out;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
