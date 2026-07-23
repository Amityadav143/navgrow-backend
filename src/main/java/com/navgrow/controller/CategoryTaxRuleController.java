/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import com.navgrow.entity.CategoryTaxRule;
import com.navgrow.exception.BadRequestException;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.CategoryTaxRuleRepository;
import com.navgrow.repository.ProductRepository;
import com.navgrow.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin management of HSN/SAC and GST rates per product category.
 *
 * Lets the office set tax once per category instead of per product, and apply a
 * category's rule across its existing products in one action when a rate or
 * classification changes.
 */
@RestController
@RequestMapping("/tax-rules")
@RequiredArgsConstructor
@Slf4j
public class CategoryTaxRuleController {

    private final CategoryTaxRuleRepository repo;
    private final ProductRepository productRepo;
    private final AuditService audit;

    @Data
    public static class RuleReq {
        @NotBlank @Size(max = 120)
        String category;

        @Size(max = 12)
        String hsnCode;

        /** Indian GST slabs run 0–28%. */
        @DecimalMin(value = "0.0", message = "GST rate cannot be negative")
        @DecimalMax(value = "28.0", message = "GST rate cannot exceed 28%")
        BigDecimal gstRate;

        @Size(max = 255) String description;
        Boolean active;
    }

    /** Readable by any signed-in staff member; the shop itself does not need it. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('EDITOR')")
    public ResponseEntity<List<CategoryTaxRule>> list() {
        return ResponseEntity.ok(repo.findAllByOrderByCategoryAsc());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<CategoryTaxRule> create(@Valid @RequestBody RuleReq req) {
        String category = req.getCategory().trim();
        if (repo.existsByCategoryIgnoreCase(category))
            throw new BadRequestException("A tax rule already exists for category '" + category + "'.");

        CategoryTaxRule rule = CategoryTaxRule.builder()
                .category(category)
                .hsnCode(blankToNull(req.getHsnCode()))
                .gstRate(req.getGstRate() != null ? req.getGstRate() : new BigDecimal("18.00"))
                .description(blankToNull(req.getDescription()))
                .active(req.getActive() == null || req.getActive())
                .build();
        CategoryTaxRule saved = repo.save(rule);
        audit.log("TAX_RULE_CREATE", "CategoryTaxRule", saved.getId().toString(),
                  category + " → HSN " + saved.getHsnCode() + " @ " + saved.getGstRate() + "%");
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<CategoryTaxRule> update(@PathVariable UUID id, @Valid @RequestBody RuleReq req) {
        CategoryTaxRule rule = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CategoryTaxRule", id.toString()));
        rule.setCategory(req.getCategory().trim());
        rule.setHsnCode(blankToNull(req.getHsnCode()));
        if (req.getGstRate() != null) rule.setGstRate(req.getGstRate());
        rule.setDescription(blankToNull(req.getDescription()));
        if (req.getActive() != null) rule.setActive(req.getActive());
        CategoryTaxRule saved = repo.save(rule);
        audit.log("TAX_RULE_UPDATE", "CategoryTaxRule", id.toString(),
                  saved.getCategory() + " → HSN " + saved.getHsnCode() + " @ " + saved.getGstRate() + "%");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("CategoryTaxRule", id.toString());
        repo.deleteById(id);
        audit.log("TAX_RULE_DELETE", "CategoryTaxRule", id.toString(), "deleted");
        return ResponseEntity.noContent().build();
    }

    /**
     * Applies a category's rule to every product in that category.
     *
     * {@code onlyMissing=true} (the default) fills gaps and leaves deliberate
     * per-product overrides alone; {@code false} forces the whole category onto
     * the rule, which is what you want after a slab change.
     */
    @PostMapping("/{id}/apply")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<Map<String, Object>> applyToProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean onlyMissing) {

        CategoryTaxRule rule = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CategoryTaxRule", id.toString()));

        var products = productRepo.findAll().stream()
                .filter(p -> p.getCategory() != null && p.getCategory().equalsIgnoreCase(rule.getCategory()))
                .toList();

        int updated = 0;
        for (var p : products) {
            boolean missing = p.getHsnCode() == null || p.getHsnCode().isBlank();
            if (onlyMissing && !missing) continue;
            p.setHsnCode(rule.getHsnCode());
            p.setGstRate(rule.getGstRate());
            productRepo.save(p);
            updated++;
        }
        audit.log("TAX_RULE_APPLY", "CategoryTaxRule", id.toString(),
                  rule.getCategory() + ": " + updated + " product(s) updated (onlyMissing=" + onlyMissing + ")");
        return ResponseEntity.ok(Map.of(
                "category", rule.getCategory(),
                "matched", products.size(),
                "updated", updated,
                "onlyMissing", onlyMissing));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
