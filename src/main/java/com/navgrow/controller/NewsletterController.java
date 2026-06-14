/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.NewsletterSubscriber;
import com.navgrow.repository.NewsletterRepository;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController @RequestMapping("/newsletter") @RequiredArgsConstructor
public class NewsletterController {
    private final NewsletterRepository repo;

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String,String>> subscribe(@RequestParam @Email @NotBlank String email, @RequestParam(required=false) String name) {
        repo.findByEmail(email).ifPresentOrElse(
            sub -> { if (!sub.isActive()) { sub.setActive(true); sub.setUnsubscribedAt(null); repo.save(sub); } },
            () -> repo.save(NewsletterSubscriber.builder().email(email).name(name).build())
        );
        return ResponseEntity.ok(Map.of("message","Subscribed successfully!"));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String,String>> unsubscribe(@RequestParam @Email @NotBlank String email) {
        repo.findByEmail(email).ifPresent(sub -> { sub.setActive(false); sub.setUnsubscribedAt(LocalDateTime.now()); repo.save(sub); });
        return ResponseEntity.ok(Map.of("message","Unsubscribed successfully."));
    }

    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String,Long>> count() {
        return ResponseEntity.ok(Map.of("activeSubscribers", repo.countByActiveTrue(), "total", repo.count()));
    }
}