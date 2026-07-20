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

import com.navgrow.entity.CatalogItem;
import com.navgrow.exception.BadRequestException;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.CatalogItemRepository;
import com.navgrow.util.SlugUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Admin-managed catalog: product categories, services, gallery categories and
 * news categories.
 *
 *  · GET /catalog?type=PRODUCT_CATEGORY       — public, active items only
 *  · GET /catalog/manage                      — admin, everything
 *  · POST/PUT/DELETE                          — admin/manager (see SecurityConfig)
 */
@RestController @RequestMapping("/catalog") @RequiredArgsConstructor
public class CatalogController {

    private static final Set<String> TYPES = Set.of(
        CatalogItem.TYPE_PRODUCT_CATEGORY, CatalogItem.TYPE_SERVICE,
        CatalogItem.TYPE_GALLERY_CATEGORY, CatalogItem.TYPE_NEWS_CATEGORY);

    private final CatalogItemRepository repo;
    private final SlugUtil slugUtil;
    private final com.navgrow.service.AuditService audit;

    @Data public static class CatalogReq {
        @NotBlank String itemType;
        @NotBlank String name;
        String slug, description, icon, imageUrl;
        Integer sortOrder;
        Boolean active;
    }

    @GetMapping
    @Cacheable(value = CacheConfig.CATALOG_PUBLIC, key = "#type")
    public ResponseEntity<List<CatalogItem>> listPublic(@RequestParam String type) {
        validateType(type);
        return ResponseEntity.ok(repo.findByItemTypeAndActiveTrueOrderBySortOrderAscNameAsc(type));
    }

    @GetMapping("/manage") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<CatalogItem>> listAll(@RequestParam(required = false) String type) {
        if (type != null) { validateType(type); return ResponseEntity.ok(repo.findByItemTypeOrderBySortOrderAscNameAsc(type)); }
        return ResponseEntity.ok(repo.findAllByOrderByItemTypeAscSortOrderAscNameAsc());
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @CacheEvict(value = {CacheConfig.CATALOG_PUBLIC, CacheConfig.PRODUCT_CATEGORIES}, allEntries = true)
    public ResponseEntity<CatalogItem> create(@Valid @RequestBody CatalogReq req) {
        validateType(req.getItemType());
        String slug = (req.getSlug() != null && !req.getSlug().isBlank())
            ? slugUtil.toSlug(req.getSlug())
            : slugUtil.uniqueSlug(req.getName(), s -> repo.findByItemTypeAndSlug(req.getItemType(), s).isPresent());
        if (repo.findByItemTypeAndSlug(req.getItemType(), slug).isPresent())
            throw new BadRequestException("An item with slug '" + slug + "' already exists for this type.");
        CatalogItem item = CatalogItem.builder()
            .itemType(req.getItemType()).name(req.getName().trim()).slug(slug)
            .description(req.getDescription()).icon(req.getIcon()).imageUrl(req.getImageUrl())
            .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
            .active(req.getActive() == null || req.getActive())
            .build();
        CatalogItem saved = repo.save(item);
        audit.log("CATALOG_CREATE", saved.getItemType(), saved.getId().toString(), saved.getName());
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @CacheEvict(value = {CacheConfig.CATALOG_PUBLIC, CacheConfig.PRODUCT_CATEGORIES}, allEntries = true)
    public ResponseEntity<CatalogItem> update(@PathVariable UUID id, @Valid @RequestBody CatalogReq req) {
        validateType(req.getItemType());
        CatalogItem item = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Catalog item", id.toString()));
        if (req.getSlug() != null && !req.getSlug().isBlank()) {
            String slug = slugUtil.toSlug(req.getSlug());
            repo.findByItemTypeAndSlug(req.getItemType(), slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new BadRequestException("An item with slug '" + slug + "' already exists for this type."); });
            item.setSlug(slug);
        }
        item.setItemType(req.getItemType()); item.setName(req.getName().trim());
        item.setDescription(req.getDescription()); item.setIcon(req.getIcon()); item.setImageUrl(req.getImageUrl());
        if (req.getSortOrder() != null) item.setSortOrder(req.getSortOrder());
        if (req.getActive()    != null) item.setActive(req.getActive());
        CatalogItem saved = repo.save(item);
        audit.log("CATALOG_UPDATE", saved.getItemType(), saved.getId().toString(), saved.getName());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @CacheEvict(value = {CacheConfig.CATALOG_PUBLIC, CacheConfig.PRODUCT_CATEGORIES}, allEntries = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        repo.deleteById(id);
        audit.log("CATALOG_DELETE", "CatalogItem", id.toString(), null);
        return ResponseEntity.noContent().build();
    }

    private void validateType(String type) {
        if (!TYPES.contains(type))
            throw new BadRequestException("Unknown catalog type '" + type + "'. Allowed: " + String.join(", ", TYPES));
    }
}
