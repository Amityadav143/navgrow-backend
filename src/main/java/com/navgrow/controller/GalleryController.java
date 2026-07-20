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
import com.navgrow.entity.GalleryItem;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.GalleryItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/gallery") @RequiredArgsConstructor
public class GalleryController {
    private final GalleryItemRepository repo;
    private final com.navgrow.service.AuditService audit;

    @Data public static class GalleryReq {
        @NotBlank String title, imageUrl;
        String category, location, year, altText;
        int sortOrder;
    }

    @GetMapping @Cacheable(value = CacheConfig.GALLERY_PUBLIC, key = "#category == null ? 'all' : #category") public ResponseEntity<List<GalleryItem>> list(@RequestParam(required=false) String category) {
        return ResponseEntity.ok(category != null
            ? repo.findByCategoryAndActiveTrueOrderBySortOrderAsc(category)
            : repo.findByActiveTrueOrderBySortOrderAscCreatedAtDesc());
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheConfig.GALLERY_PUBLIC, allEntries = true)
    public ResponseEntity<GalleryItem> create(@Valid @RequestBody GalleryReq req) {
        GalleryItem item = GalleryItem.builder().title(req.getTitle())
            .category(req.getCategory() != null ? req.getCategory() : "Projects")
            .location(req.getLocation()).year(req.getYear())
            .imageUrl(req.getImageUrl()).altText(req.getAltText())
            .sortOrder(req.getSortOrder()).build();
        GalleryItem saved = repo.save(item);
        audit.log("GALLERY_CREATE", "GalleryItem", saved.getId().toString(), saved.getTitle());
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheConfig.GALLERY_PUBLIC, allEntries = true)
    public ResponseEntity<GalleryItem> update(@PathVariable UUID id, @Valid @RequestBody GalleryReq req) {
        GalleryItem item = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GalleryItem", id.toString()));
        item.setTitle(req.getTitle());
        if (req.getCategory() != null) item.setCategory(req.getCategory());
        if (req.getLocation()  != null) item.setLocation(req.getLocation());
        if (req.getYear()      != null) item.setYear(req.getYear());
        item.setImageUrl(req.getImageUrl());
        if (req.getAltText()   != null) item.setAltText(req.getAltText());
        item.setSortOrder(req.getSortOrder());
        GalleryItem saved = repo.save(item);
        audit.log("GALLERY_UPDATE", "GalleryItem", saved.getId().toString(), saved.getTitle());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheConfig.GALLERY_PUBLIC, allEntries = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        GalleryItem item = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("GalleryItem", id.toString()));
        item.setActive(false);
        repo.save(item);
        audit.log("GALLERY_DEACTIVATE", "GalleryItem", id.toString(), item.getTitle());
        return ResponseEntity.noContent().build();
    }
}