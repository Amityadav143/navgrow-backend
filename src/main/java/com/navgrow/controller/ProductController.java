/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 · navgrow.org
 */
package com.navgrow.controller;
import com.navgrow.entity.Product;
import com.navgrow.exception.*;
import com.navgrow.repository.ProductRepository;
import com.navgrow.util.SlugUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductRepository repo;
    private final SlugUtil slugUtil;

    /** Columns the public list endpoint is allowed to sort by (prevents 500s from bad input). */
    private static final Set<String> SORTABLE = Set.of(
        "name", "price", "mrp", "createdAt", "rating", "stockQty", "category");

    @Data
    public static class ProductRequest {
        @NotBlank String name;
        @NotBlank String category;
        String description;
        @NotNull @Positive BigDecimal price;
        BigDecimal mrp;
        BigDecimal gstRate;
        Integer stockQty;
        String badge;
        String imageUrl;
        boolean featured;
        Boolean active;
        Integer minOrderQty;
        // Rich detail fields (shown on the product detail page)
        String tagline;
        String summary;
        String warranty;
        String imageUrls;
        String features;
        String benefits;
        String applications;
        String specifications;
    }

    @GetMapping
    public ResponseEntity<Page<Product>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "name") String sort) {
        // Guard page/size and whitelist the sort column so bad input can't 500.
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String safeSort = SORTABLE.contains(sort) ? sort : "name";
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(safeSort));
        Page<Product> result;
        if (q != null && !q.isBlank()) {
            result = repo.search(q, pageable);
        } else if (category != null && !category.isBlank()) {
            result = repo.findByCategoryAndActiveTrue(category, pageable);
        } else {
            result = repo.findByActiveTrue(pageable);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/featured")
    public ResponseEntity<List<Product>> featured() {
        return ResponseEntity.ok(repo.findByFeaturedTrueAndActiveTrue());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(repo.findAllCategories());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Product> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(repo.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.status(201).body(repo.save(buildFromRequest(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest req) {
        Product p = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        applyToEntity(req, p);   // name/slug stay stable on update (slug not regenerated)
        return ResponseEntity.ok(repo.save(p));
    }

    /**
     * Bulk create products in a single transactional request.
     * Far faster and safer than the client firing one POST per row:
     * either the whole batch is validated and persisted, or none of it is.
     * Returns the created products.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> bulkCreate(@Valid @RequestBody List<ProductRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            throw new BadRequestException("No products provided for bulk upload");
        }
        if (reqs.size() > 500) {
            throw new BadRequestException("Bulk upload is limited to 500 products per request");
        }
        List<Product> toSave = new ArrayList<>(reqs.size());
        for (ProductRequest req : reqs) {
            toSave.add(buildFromRequest(req));
        }
        List<Product> saved = repo.saveAll(toSave);
        Map<String, Object> body = new HashMap<>();
        body.put("created", saved.size());
        body.put("products", saved);
        return ResponseEntity.status(201).body(body);
    }

    /** Quick stock adjustment without sending the whole product payload. */
    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> updateStock(@PathVariable UUID id, @RequestParam Integer qty) {
        if (qty == null || qty < 0) throw new BadRequestException("Stock quantity must be zero or positive");
        Product p = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        p.setStockQty(qty);
        return ResponseEntity.ok(repo.save(p));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Product p = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        p.setActive(false);
        repo.save(p);
        return ResponseEntity.noContent().build();
    }

    /* ── Shared mapping (single source of truth for all create/update/bulk paths) ── */

    /** Builds a brand-new Product (generates slug + SKU) from a request. */
    private Product buildFromRequest(ProductRequest req) {
        String slug = slugUtil.uniqueSlug(req.getName(), repo::existsBySlug);
        Product p = Product.builder()
            .sku("NGP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4))
            .slug(slug)
            .build();
        applyToEntity(req, p);
        return p;
    }

    /** Copies every editable field from the request onto an existing entity. */
    private void applyToEntity(ProductRequest req, Product p) {
        p.setName(req.getName());
        p.setCategory(req.getCategory());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setMrp(req.getMrp());
        p.setGstRate(req.getGstRate() != null ? req.getGstRate() : new BigDecimal("18"));
        if (req.getStockQty() != null) p.setStockQty(req.getStockQty());
        p.setBadge(req.getBadge());
        p.setImageUrl(req.getImageUrl());
        p.setFeatured(req.isFeatured());
        if (req.getActive() != null) p.setActive(req.getActive());
        if (req.getMinOrderQty() != null) p.setMinOrderQty(req.getMinOrderQty());
        p.setTagline(req.getTagline());
        p.setSummary(req.getSummary());
        p.setWarranty(req.getWarranty());
        p.setImageUrls(req.getImageUrls());
        p.setFeatures(req.getFeatures());
        p.setBenefits(req.getBenefits());
        p.setApplications(req.getApplications());
        p.setSpecifications(req.getSpecifications());
    }
}
