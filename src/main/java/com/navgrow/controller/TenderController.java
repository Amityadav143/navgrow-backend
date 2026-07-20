/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.navgrow.config.CacheConfig;
import com.navgrow.entity.Tender;
import com.navgrow.enums.TenderStatus;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.TenderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController @RequestMapping("/tenders") @RequiredArgsConstructor
public class TenderController {
    private final TenderRepository repo;
    private final com.navgrow.service.AuditService audit;

    @Data public static class TenderReq {
        /** Optional — auto-generated (NAV-TND-yyyyMMdd-xxxx) when blank so the
         *  admin form no longer 400s for a field it never displayed. */
        String refNumber;
        @NotBlank String title;
        String description;
        BigDecimal valueMin, valueMax;
        LocalDate deadline;
        TenderStatus status;
        boolean featured;
        String applyLink, organization, location, category, imageUrl, documentUrl;
        /** Admin UI convenience flag — maps to OPEN/CLOSED when status absent. */
        Boolean active;

        TenderStatus resolvedStatus(TenderStatus fallback) {
            if (status != null) return status;
            if (active != null) return active ? TenderStatus.OPEN : TenderStatus.CLOSED;
            return fallback;
        }
    }

    @GetMapping @Cacheable(value = CacheConfig.TENDERS_PUBLIC, key = "'open'") public ResponseEntity<List<Tender>> listOpen() { return ResponseEntity.ok(repo.findByStatusOrderByDeadlineAsc(TenderStatus.OPEN)); }
    @GetMapping("/featured") @Cacheable(value = CacheConfig.TENDERS_PUBLIC, key = "'featured'") public ResponseEntity<List<Tender>> featured() { return ResponseEntity.ok(repo.findByFeaturedTrueAndStatusOrderByDeadlineAsc(TenderStatus.OPEN)); }

    /** Admin listing — every status, newest first, so closed/awarded tenders stay manageable. */
    @GetMapping("/manage") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<Tender>> listAll() {
        return ResponseEntity.ok(repo.findAll(Sort.by("createdAt").descending()));
    }

    private String generateRefNumber() {
        String base = "NAV-TND-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        String ref;
        do { ref = base + String.format("%04d", new Random().nextInt(10000)); }
        while (repo.existsByRefNumber(ref));
        return ref;
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheConfig.TENDERS_PUBLIC, allEntries = true)
    public ResponseEntity<Tender> create(@Valid @RequestBody TenderReq req) {
        String ref = (req.getRefNumber() == null || req.getRefNumber().isBlank())
            ? generateRefNumber() : req.getRefNumber().trim();
        if (repo.existsByRefNumber(ref))
            throw new com.navgrow.exception.BadRequestException("A tender with reference number '" + ref + "' already exists.");
        Tender t = Tender.builder().refNumber(ref).title(req.getTitle())
            .description(req.getDescription()).valueMin(req.getValueMin()).valueMax(req.getValueMax())
            .deadline(req.getDeadline() != null ? req.getDeadline() : LocalDate.now().plusMonths(1))
            .status(req.resolvedStatus(TenderStatus.OPEN))
            .applyLink(req.getApplyLink()).organization(req.getOrganization())
            .location(req.getLocation()).category(req.getCategory())
            .imageUrl(req.getImageUrl()).documentUrl(req.getDocumentUrl())
            .featured(req.isFeatured()).build();
        Tender saved = repo.save(t);
        audit.log("TENDER_CREATE", "Tender", saved.getId().toString(), saved.getTitle());
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheConfig.TENDERS_PUBLIC, allEntries = true)
    public ResponseEntity<Tender> update(@PathVariable UUID id, @Valid @RequestBody TenderReq req) {
        Tender t = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        if (req.getRefNumber() != null && !req.getRefNumber().isBlank()) t.setRefNumber(req.getRefNumber().trim());
        t.setTitle(req.getTitle()); t.setDescription(req.getDescription());
        t.setValueMin(req.getValueMin()); t.setValueMax(req.getValueMax());
        if (req.getDeadline() != null) t.setDeadline(req.getDeadline());
        t.setApplyLink(req.getApplyLink()); t.setOrganization(req.getOrganization());
        t.setLocation(req.getLocation()); t.setCategory(req.getCategory());
        t.setImageUrl(req.getImageUrl()); t.setDocumentUrl(req.getDocumentUrl());
        t.setFeatured(req.isFeatured());
        t.setStatus(req.resolvedStatus(t.getStatus()));
        Tender saved = repo.save(t);
        audit.log("TENDER_UPDATE", "Tender", saved.getId().toString(), saved.getTitle());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheConfig.TENDERS_PUBLIC, allEntries = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.deleteById(id);
        audit.log("TENDER_DELETE", "Tender", id.toString(), null);
        return ResponseEntity.noContent().build();
    }
}