/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 · navgrow.org
 */
package com.navgrow.controller;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.navgrow.config.CacheConfig;
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
    private final com.navgrow.repository.CatalogItemRepository catalogRepo;
    private final com.navgrow.service.AuditService audit;
    private final com.navgrow.repository.CategoryTaxRuleRepository taxRuleRepo;

    /** Columns the public list endpoint is allowed to sort by (prevents 500s from bad input). */
    private static final Set<String> SORTABLE = Set.of(
        "name", "price", "mrp", "createdAt", "rating", "stockQty", "category");

    @Data
    public static class ProductRequest {
        @NotBlank String name;
        /** Optional admin SKU (letters, digits, - _ / .). Blank → auto-generated "NGP-…". */
        @Size(max = 64) String sku;
        @NotBlank String category;
        String description;
        @NotNull @Positive BigDecimal price;
        BigDecimal mrp;
        BigDecimal gstRate;
        String hsnCode;
        Integer stockQty;
        BigDecimal deliveryCharge;   // per-unit delivery base for this product (null = zone default)
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
        // Limited delivery area. Null deliveryRestricted = "not sent" (e.g. an
        // older client or a CSV without these columns) → existing values are kept.
        Boolean deliveryRestricted;
        String allowedPincodes;      // "734001, 7340*, 734101-734105"
        @Size(max = 255) String deliveryAreaNote;
    }

    @GetMapping
    public ResponseEntity<Page<Product>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(required = false) String direction) {
        // Guard page/size and whitelist the sort column so bad input can't 500.
        int safePage = Math.max(0, page);
        // 300: the shop filters/sorts client-side over the whole catalogue, so a
        // low cap silently hid every product past the first page.
        int safeSize = Math.min(Math.max(1, size), 300);
        String safeSort = SORTABLE.contains(sort) ? sort : "name";
        // Explicit direction wins; otherwise sensible defaults per column
        // (newest/highest first for time- and quality-ranked columns, A→Z for names).
        Sort.Direction dir;
        if ("desc".equalsIgnoreCase(direction)) {
            dir = Sort.Direction.DESC;
        } else if ("asc".equalsIgnoreCase(direction)) {
            dir = Sort.Direction.ASC;
        } else {
            dir = ("createdAt".equals(safeSort) || "rating".equals(safeSort))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(dir, safeSort));
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

    /**
     * Live "related products" — same category, active, excludes the product
     * itself, featured first. Replaces the frontend's static-data fallback so
     * admin-created products also get a related section.
     */
    @GetMapping("/{id}/related")
    public ResponseEntity<List<Product>> related(@PathVariable UUID id,
                                                 @RequestParam(defaultValue = "4") int limit) {
        Product p = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        List<Product> rel = repo.findTop8ByCategoryAndActiveTrueAndIdNotOrderByFeaturedDescCreatedAtDesc(p.getCategory(), id);
        return ResponseEntity.ok(rel.subList(0, Math.min(Math.max(limit, 1), rel.size())));
    }

    @GetMapping("/categories")
    @Cacheable(CacheConfig.PRODUCT_CATEGORIES)
    public ResponseEntity<List<String>> categories() {
        // Admin-defined categories (Catalog) first, then any legacy categories
        // still present on products — merged and de-duplicated.
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        catalogRepo.findByItemTypeAndActiveTrueOrderBySortOrderAscNameAsc(
                com.navgrow.entity.CatalogItem.TYPE_PRODUCT_CATEGORY)
            .forEach(c -> merged.add(c.getName()));
        repo.findAllCategories().forEach(merged::add);
        return ResponseEntity.ok(new java.util.ArrayList<>(merged));
    }

    /**
     * Admin catalogue view — EVERY product, including drafts and deactivated ones
     * (the public list only returns active products, so drafts used to vanish from
     * the admin screen and deleted products could never be restored).
     * status: active | inactive | restricted | outofstock | lowstock (optional).
     */
    @GetMapping("/manage")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasAuthority('PERM_PRODUCTS')")
    public ResponseEntity<Page<Product>> manage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        org.springframework.data.jpa.domain.Specification<Product> spec = (root, cq, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> ps = new java.util.ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(
                    cb.like(cb.lower(root.<String>get("name")), like),
                    cb.like(cb.lower(root.<String>get("sku")), like),
                    cb.like(cb.lower(root.<String>get("category")), like)));
            }
            if (category != null && !category.isBlank()) ps.add(cb.equal(root.<String>get("category"), category.trim()));
            String st = status == null ? "" : status.trim().toLowerCase();
            switch (st) {
                case "active"     -> ps.add(cb.isTrue(root.<Boolean>get("active")));
                case "inactive"   -> ps.add(cb.isFalse(root.<Boolean>get("active")));
                case "restricted" -> ps.add(cb.isTrue(root.<Boolean>get("deliveryRestricted")));
                case "outofstock" -> ps.add(cb.lessThanOrEqualTo(root.<Integer>get("stockQty"), 0));
                case "lowstock"   -> ps.add(cb.lessThan(root.<Integer>get("stockQty"), 10));
                default -> { /* all */ }
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(repo.findAll(spec, pageable));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Product> getBySlug(@PathVariable String slug) {
        Product p = repo.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        // Drafts and removed products are invisible to customers (they used to stay
        // reachable by URL and failed only at checkout); staff can still preview them.
        if (!p.isActive() && !canManageProducts()) {
            throw new ResourceNotFoundException("Product not found: " + slug);
        }
        return ResponseEntity.ok(p);
    }

    private static boolean canManageProducts() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_SUPER_ADMIN".equals(r) || "ROLE_ADMIN".equals(r) || "PERM_PRODUCTS".equals(r);
        });
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasAuthority('PERM_PRODUCTS')")
    @CacheEvict(value = CacheConfig.PRODUCT_CATEGORIES, allEntries = true)
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) {
        Product saved = repo.save(buildFromRequest(req));
        audit.log("PRODUCT_CREATE", "Product", saved.getId().toString(), saved.getName());
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasAuthority('PERM_PRODUCTS')")
    @CacheEvict(value = CacheConfig.PRODUCT_CATEGORIES, allEntries = true)
    public ResponseEntity<Product> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest req) {
        Product p = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        applyToEntity(req, p);   // name/slug stay stable on update (slug not regenerated)
        // SKU: only validated when actually changed, so legacy SKUs never block an edit.
        String rawSku = req.getSku();
        if (rawSku != null && !rawSku.isBlank() && !rawSku.trim().equalsIgnoreCase(p.getSku())) {
            String sku = cleanSku(rawSku);
            ensureSkuFree(sku, p.getId());
            p.setSku(sku);
        }
        Product saved = repo.save(p);
        audit.log("PRODUCT_UPDATE", "Product", saved.getId().toString(), saved.getName());
        return ResponseEntity.ok(saved);
    }

    /**
     * Bulk create products in a single transactional request.
     * Far faster and safer than the client firing one POST per row:
     * either the whole batch is validated and persisted, or none of it is.
     * Returns the created products.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasAuthority('PERM_PRODUCTS')")
    @org.springframework.transaction.annotation.Transactional
    @CacheEvict(value = CacheConfig.PRODUCT_CATEGORIES, allEntries = true)
    public ResponseEntity<Map<String, Object>> bulkCreate(@Valid @RequestBody List<ProductRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            throw new BadRequestException("No products provided for bulk upload");
        }
        if (reqs.size() > 500) {
            throw new BadRequestException("Bulk upload is limited to 500 products per request");
        }
        List<Product> toSave = new ArrayList<>(reqs.size());
        Set<String> batchSkus = new HashSet<>();
        int row = 1;
        for (ProductRequest req : reqs) {
            row++;   // row 1 is the CSV header
            String sku = cleanSku(req.getSku());
            if (sku != null && !batchSkus.add(sku)) {
                throw new BadRequestException("Row " + row + ": SKU " + sku + " appears more than once in this file.");
            }
            toSave.add(buildFromRequest(req));
        }
        List<Product> saved = repo.saveAll(toSave);
        audit.log("PRODUCT_BULK_CREATE", "Product", null, saved.size() + " products imported via CSV");
        Map<String, Object> body = new HashMap<>();
        body.put("created", saved.size());
        body.put("products", saved);
        return ResponseEntity.status(201).body(body);
    }

    /** Quick stock adjustment without sending the whole product payload. */
    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasAuthority('PERM_PRODUCTS')")
    public ResponseEntity<Product> updateStock(@PathVariable UUID id, @RequestParam Integer qty) {
        if (qty == null || qty < 0) throw new BadRequestException("Stock quantity must be zero or positive");
        Product p = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        p.setStockQty(qty);
        return ResponseEntity.ok(repo.save(p));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasAuthority('PERM_PRODUCTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Product p = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        p.setActive(false);
        repo.save(p);
        audit.log("PRODUCT_DEACTIVATE", "Product", id.toString(), p.getName());
        return ResponseEntity.noContent().build();
    }

    /* ── Shared mapping (single source of truth for all create/update/bulk paths) ── */

    /** Builds a brand-new Product (generates slug + SKU) from a request. */
    private Product buildFromRequest(ProductRequest req) {
        String slug = slugUtil.uniqueSlug(req.getName(), repo::existsBySlug);
        String sku = cleanSku(req.getSku());
        if (sku != null) ensureSkuFree(sku, null);
        Product p = Product.builder()
            .sku(sku != null ? sku
                 : "NGP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4))
            .slug(slug)
            .build();
        applyToEntity(req, p);
        return p;
    }

    /** Trimmed, upper-cased admin SKU, or null when blank. Rejects odd characters. */
    private static String cleanSku(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase();
        if (!s.matches("^[A-Z0-9][A-Z0-9._/-]{0,63}$")) {
            throw new BadRequestException("SKU may use only letters, digits, '-', '_', '/' and '.' (max 64 characters).");
        }
        return s;
    }

    /** SKUs are unique — a clear message beats a database constraint error. */
    private void ensureSkuFree(String sku, UUID selfId) {
        repo.findBySku(sku).ifPresent(other -> {
            if (selfId == null || !other.getId().equals(selfId)) {
                throw new BadRequestException("SKU " + sku + " is already used by \"" + other.getName() + "\".");
            }
        });
    }

    /** Copies every editable field from the request onto an existing entity. */
    private void applyToEntity(ProductRequest req, Product p) {
        p.setName(req.getName());
        p.setCategory(req.getCategory());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setMrp(req.getMrp());
        // Tax: an explicit product value always wins. When the request leaves HSN or
        // the rate blank we fall back to the admin-managed rule for the category,
        // and only then to the statutory default, so a bulk import or a quick
        // "add product" never silently lands on the wrong slab.
        var taxRule = (req.getCategory() != null && !req.getCategory().isBlank())
                ? taxRuleRepo.findByCategoryIgnoreCase(req.getCategory().trim()).orElse(null)
                : null;

        if (req.getHsnCode() != null && !req.getHsnCode().isBlank()) {
            p.setHsnCode(req.getHsnCode().trim());
        } else if (taxRule != null && taxRule.getHsnCode() != null) {
            p.setHsnCode(taxRule.getHsnCode());
        }

        if (req.getGstRate() != null) {
            p.setGstRate(req.getGstRate());
        } else if (taxRule != null && taxRule.getGstRate() != null) {
            p.setGstRate(taxRule.getGstRate());
        } else {
            p.setGstRate(new BigDecimal("18"));
        }
        if (req.getStockQty() != null) p.setStockQty(req.getStockQty());
        p.setDeliveryCharge(req.getDeliveryCharge());   // null clears it → zone default
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
        applyDeliveryArea(req, p);
    }

    /**
     * Validates + stores the optional "deliver only to these pincodes" rule. The
     * list is normalised (canonical entries, duplicates removed) and rejected with
     * a clear message if any entry is malformed, so an admin can never save a rule
     * that silently blocks every buyer.
     */
    private void applyDeliveryArea(ProductRequest req, Product p) {
        if (req.getDeliveryRestricted() == null) return;          // not sent → unchanged
        String normalized;
        try {
            normalized = com.navgrow.util.PincodeRules.normalize(req.getAllowedPincodes());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        boolean restricted = req.getDeliveryRestricted();
        if (restricted && normalized == null) {
            throw new BadRequestException("Add at least one pincode, prefix (e.g. 734*) or range for the limited "
                + "delivery area — or switch the restriction off.");
        }
        p.setDeliveryRestricted(restricted);
        // Keep the list even when switched off, so the admin can toggle it back on.
        p.setAllowedPincodes(normalized);
        String note = req.getDeliveryAreaNote();
        p.setDeliveryAreaNote(note == null || note.isBlank() ? null : note.trim());
    }
}
