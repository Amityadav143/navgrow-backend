/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import com.navgrow.entity.StockNotification;
import com.navgrow.repository.ProductRepository;
import com.navgrow.repository.StockNotificationRepository;
import com.navgrow.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Notify me when back in stock" — turns out-of-stock dead-ends into captured
 * leads. Public endpoint to subscribe; admin endpoints to see pent-up demand and
 * notify everyone once a product is restocked.
 */
@Slf4j
@RestController
@RequestMapping("/stock-notifications")
@RequiredArgsConstructor
public class StockNotificationController {

    private final StockNotificationRepository repo;
    private final ProductRepository productRepo;
    private final EmailService emailService;

    // ── Public: subscribe ────────────────────────────────────────────────────
    public static class SubscribeRequest {
        @Email @NotBlank public String email;
        public String name;
        @NotBlank public String productSlug;
        public String productName;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> subscribe(@Valid @RequestBody SubscribeRequest req) {
        String email = req.email.trim().toLowerCase();
        String slug  = req.productSlug.trim();

        // Dedupe: one active request per email+product.
        var existing = repo.findByEmailIgnoreCaseAndProductSlug(email, slug);
        if (existing.isPresent()) {
            var n = existing.get();
            if (n.isNotified()) { // they want to be told again after a new sell-out
                n.setNotified(false);
                n.setNotifiedAt(null);
                repo.save(n);
            }
            return ResponseEntity.ok(Map.of("message", "You're on the list — we'll email you the moment it's back."));
        }

        var saved = StockNotification.builder()
            .email(email)
            .name(req.name != null ? req.name.trim() : null)
            .productSlug(slug)
            .productName(req.productName != null ? req.productName.trim() : null)
            .productId(productRepo.findBySlug(slug).map(p -> p.getId()).orElse(null))
            .build();
        repo.save(saved);

        // Best-effort confirmation email — never fail the request on email trouble.
        try {
            String pname = saved.getProductName() != null ? saved.getProductName() : "the product";
            emailService.sendReplyEmail(email, saved.getName() != null ? saved.getName() : "there",
                "We'll notify you when it's back in stock",
                "Thanks for your interest in " + pname + ".\n\n" +
                "We've noted your request and will email you as soon as it's available again. " +
                "Need it urgently or in bulk? Reply to this email or reach us on WhatsApp at +91 89270 70972 " +
                "and our team will help right away.\n\n— Navgrow Engineering");
        } catch (Exception e) {
            log.warn("Stock-notify confirmation email failed for {}: {}", email, e.getMessage());
        }
        return ResponseEntity.ok(Map.of("message", "Done! We'll email you the moment it's back in stock."));
    }

    // ── Admin: pent-up demand grouped by product ─────────────────────────────
    @GetMapping("/demand")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER') or hasAuthority('PERM_PRODUCTS')")
    public ResponseEntity<List<Map<String, Object>>> demand() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : repo.demandByProduct()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productSlug", row[0]);
            m.put("productName", row[1]);
            m.put("count", row[2]);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    // ── Admin: full pending list ─────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER') or hasAuthority('PERM_PRODUCTS')")
    public ResponseEntity<List<StockNotification>> pending() {
        return ResponseEntity.ok(repo.findByNotifiedFalseOrderByCreatedAtDesc());
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER') or hasAuthority('PERM_PRODUCTS')")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok(Map.of("pending", repo.countByNotifiedFalse()));
    }

    // ── Admin: notify everyone waiting for a restocked product ───────────────
    @PostMapping("/notify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER') or hasAuthority('PERM_PRODUCTS')")
    public ResponseEntity<Map<String, Object>> notifyRestocked(@RequestParam String productSlug) {
        var waiting = repo.findByProductSlugAndNotifiedFalse(productSlug.trim());
        int sent = 0;
        String productName = waiting.stream().map(StockNotification::getProductName)
            .filter(n -> n != null).findFirst().orElse("A product you wanted");
        String url = "https://navgrow.org/shop/" + productSlug.trim();
        for (var n : waiting) {
            try {
                emailService.sendReplyEmail(n.getEmail(), n.getName() != null ? n.getName() : "there",
                    productName + " is back in stock",
                    "Good news — " + productName + " is available again.\n\n" +
                    "Order it here while stock lasts: " + url + "\n\n" +
                    "— Navgrow Engineering");
                sent++;
            } catch (Exception e) {
                log.warn("Restock email failed for {}: {}", n.getEmail(), e.getMessage());
            }
            n.setNotified(true);
            n.setNotifiedAt(LocalDateTime.now());
        }
        repo.saveAll(waiting);
        return ResponseEntity.ok(Map.of("notified", waiting.size(), "emailsSent", sent));
    }
}
