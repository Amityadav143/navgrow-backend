/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.QuoteRequest;
import com.navgrow.enums.QuoteStatus;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.QuoteRequestRepository;
import com.navgrow.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequestMapping("/quotes") @RequiredArgsConstructor
public class QuoteController {
    private final QuoteRequestRepository repo;
    private final EmailService emailService;

    @Data
    public static class QuoteReq {
        @NotBlank String name;
        @Email @NotBlank String email;
        @NotBlank String phone;
        String company;
        @NotBlank String serviceType;
        String scope, duration, notes;
        List<String> addons;
        BigDecimal estLow, estHigh;
    }

    @PostMapping
    public ResponseEntity<Map<String,String>> submit(@Valid @RequestBody QuoteReq req) {
        QuoteRequest q = QuoteRequest.builder()
            .name(req.getName()).email(req.getEmail()).phone(req.getPhone())
            .company(req.getCompany()).serviceType(req.getServiceType())
            .scope(req.getScope()).duration(req.getDuration())
            .addons(req.getAddons()).notes(req.getNotes())
            .estLow(req.getEstLow()).estHigh(req.getEstHigh())
            .build();
        repo.save(q);
        emailService.sendQuoteAcknowledgement(req.getEmail(), req.getName(), req.getServiceType());
        return ResponseEntity.status(201).body(Map.of("message","Quote request received. We'll respond within 24 hours."));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<QuoteRequest>> list(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) QuoteStatus status) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(status != null ? repo.findByStatusOrderByCreatedAtDesc(status, p) : repo.findAllByOrderByCreatedAtDesc(p));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<QuoteRequest> updateStatus(@PathVariable UUID id, @RequestParam QuoteStatus status, @RequestParam(required=false) BigDecimal quotedAmount) {
        QuoteRequest q = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("QuoteRequest", id.toString()));
        q.setStatus(status);
        if (quotedAmount != null) q.setQuotedAmount(quotedAmount);
        return ResponseEntity.ok(repo.save(q));
    }
}