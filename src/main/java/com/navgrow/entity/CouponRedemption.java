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

/**
 * One row per (coupon, customer). This is how a "once per user" coupon such as
 * NAVGROW10 is enforced: the UNIQUE (coupon_id, user_id) constraint makes a
 * second redemption by the same account impossible, and the row is written only
 * when the order that used the coupon is actually confirmed (COD at placement,
 * online at successful payment) — so an abandoned, unpaid order never burns a
 * customer's one allowed use.
 */
@Entity
@Table(name = "coupon_redemptions",
       uniqueConstraints = @UniqueConstraint(name = "uq_coupon_user", columnNames = {"coupon_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CouponRedemption {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Builder.Default
    @Column(name = "redeemed_at", updatable = false)
    private LocalDateTime redeemedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (redeemedAt == null) redeemedAt = LocalDateTime.now();
    }
}
