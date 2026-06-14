/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.Product;
import com.navgrow.entity.ProductReview;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.ProductRepository;
import com.navgrow.repository.ProductReviewRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController @RequiredArgsConstructor
public class ReviewController {
    private final ProductReviewRepository reviewRepo;
    private final ProductRepository productRepo;

    @Data public static class ReviewRequest {
        @NotBlank String reviewerName;
        String reviewerEmail;
        @NotNull @Min(1) @Max(5) Integer rating;
        String title;
        String body;
    }

    @GetMapping("/products/{id}/reviews")
    public ResponseEntity<List<ProductReview>> list(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewRepo.findByProductIdAndApprovedTrueOrderByCreatedAtDesc(id));
    }

    @PostMapping("/products/{id}/reviews")
    public ResponseEntity<Map<String, String>> create(@PathVariable UUID id, @Valid @RequestBody ReviewRequest req) {
        Product product = productRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        ProductReview review = ProductReview.builder()
            .product(product)
            .reviewerName(req.getReviewerName())
            .reviewerEmail(req.getReviewerEmail())
            .rating(req.getRating())
            .title(req.getTitle())
            .body(req.getBody())
            .approved(false)
            .build();
        reviewRepo.save(review);
        return ResponseEntity.status(201).body(Map.of("message", "Review submitted. It will appear after moderation."));
    }

    @PatchMapping("/admin/reviews/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductReview> approve(@PathVariable UUID id) {
        ProductReview r = reviewRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Review", id.toString()));
        r.setApproved(true);
        reviewRepo.save(r);
        // Update product average rating
        Double avg = reviewRepo.avgRatingForProduct(r.getProduct().getId());
        if (avg != null) {
            Product p = r.getProduct();
            p.setRating(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
            productRepo.save(p);
        }
        return ResponseEntity.ok(r);
    }

    @GetMapping("/admin/reviews/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProductReview>> pending() {
        return ResponseEntity.ok(reviewRepo.findByApprovedFalseOrderByCreatedAtDesc());
    }

    @DeleteMapping("/admin/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { reviewRepo.deleteById(id); return ResponseEntity.noContent().build(); }
}
