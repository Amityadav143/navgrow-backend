package com.navgrow.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "coupons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Coupon {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String code;

    private String description;

    @Column(name = "coupon_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponType couponType = CouponType.PERCENTAGE;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count")
    private int usageCount = 0;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "valid_from")
    private LocalDateTime validFrom = LocalDateTime.now();

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CouponType { PERCENTAGE, FLAT }

    public boolean isValid() {
        if (!active) return false;
        if (usageLimit != null && usageCount >= usageLimit) return false;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(validFrom)) return false;
        if (validUntil != null && now.isAfter(validUntil)) return false;
        return true;
    }

    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        if (orderAmount.compareTo(minOrderAmount) < 0) return BigDecimal.ZERO;
        BigDecimal discount;
        if (couponType == CouponType.PERCENTAGE) {
            discount = orderAmount.multiply(value).divide(new BigDecimal("100"));
            if (maxDiscount != null && discount.compareTo(maxDiscount) > 0) discount = maxDiscount;
        } else {
            discount = value;
        }
        return discount.min(orderAmount);
    }
}
