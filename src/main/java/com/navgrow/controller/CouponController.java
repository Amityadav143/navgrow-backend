/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.Coupon;
import com.navgrow.exception.BadRequestException;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.CouponRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/coupons") @RequiredArgsConstructor
public class CouponController {
    private final CouponRepository repo;

    @Data public static class CouponReq {
        @NotBlank String code;
        String description;
        Coupon.CouponType couponType;
        @NotNull @PositiveOrZero BigDecimal value;  // 0 is valid for FREE_SHIP coupons
        BigDecimal minOrderAmount;
        BigDecimal maxDiscount;
        Integer usageLimit;
        LocalDateTime validFrom;
        LocalDateTime validUntil;
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestParam @NotBlank String code,
            @RequestParam @NotNull BigDecimal amount) {
        Coupon coupon = repo.findByCodeIgnoreCase(code)
            .orElseThrow(() -> new BadRequestException("Invalid coupon code."));
        if (!coupon.isValid()) throw new BadRequestException("Coupon is expired or no longer valid.");
        if (amount.compareTo(coupon.getMinOrderAmount()) < 0)
            throw new BadRequestException("Minimum order amount for this coupon is ₹" + coupon.getMinOrderAmount());
        BigDecimal discount = coupon.calculateDiscount(amount);
        return ResponseEntity.ok(Map.of(
            "valid", true,
            "code", coupon.getCode(),
            "discount", discount,
            "description", coupon.getDescription() != null ? coupon.getDescription() : "",
            "type", coupon.getCouponType()
        ));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Coupon>> list() { return ResponseEntity.ok(repo.findAll()); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> update(@PathVariable UUID id, @Valid @RequestBody CouponReq req) {
        Coupon c = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        c.setCode(req.getCode().toUpperCase());
        c.setDescription(req.getDescription());
        c.setCouponType(req.getCouponType());
        c.setValue(req.getValue());
        if (req.getMinOrderAmount() != null) c.setMinOrderAmount(req.getMinOrderAmount());
        c.setMaxDiscount(req.getMaxDiscount());
        c.setUsageLimit(req.getUsageLimit());
        if (req.getValidFrom() != null) c.setValidFrom(req.getValidFrom());
        c.setValidUntil(req.getValidUntil());
        return ResponseEntity.ok(repo.save(c));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.deleteById(id); return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> create(@Valid @RequestBody CouponReq req) {
        if (repo.findByCodeIgnoreCase(req.getCode()).isPresent())
            throw new BadRequestException("Coupon code already exists.");
        Coupon c = Coupon.builder()
            .code(req.getCode().toUpperCase())
            .description(req.getDescription())
            .couponType(req.getCouponType() != null ? req.getCouponType() : Coupon.CouponType.PERCENTAGE)
            .value(req.getValue())
            .minOrderAmount(req.getMinOrderAmount() != null ? req.getMinOrderAmount() : BigDecimal.ZERO)
            .maxDiscount(req.getMaxDiscount())
            .usageLimit(req.getUsageLimit())
            .validFrom(req.getValidFrom() != null ? req.getValidFrom() : LocalDateTime.now())
            .validUntil(req.getValidUntil())
            .build();
        return ResponseEntity.status(201).body(repo.save(c));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Coupon> toggle(@PathVariable UUID id) {
        Coupon c = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Coupon", id.toString()));
        c.setActive(!c.isActive());
        return ResponseEntity.ok(repo.save(c));
    }

}
