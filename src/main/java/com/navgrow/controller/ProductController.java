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
    }

    @GetMapping
    public ResponseEntity<Page<Product>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "name") String sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
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
        String slug = slugUtil.uniqueSlug(req.getName(), repo::existsBySlug);
        String sku  = "NGP-" + System.currentTimeMillis();
        Product p = Product.builder()
            .sku(sku).name(req.getName()).slug(slug)
            .category(req.getCategory()).description(req.getDescription())
            .price(req.getPrice()).mrp(req.getMrp())
            .gstRate(req.getGstRate() != null ? req.getGstRate() : new BigDecimal("18"))
            .stockQty(req.getStockQty() != null ? req.getStockQty() : 0)
            .badge(req.getBadge()).imageUrl(req.getImageUrl())
            .featured(req.isFeatured()).active(true)
            .build();
        return ResponseEntity.status(201).body(repo.save(p));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest req) {
        Product p = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        p.setName(req.getName()); p.setCategory(req.getCategory());
        p.setDescription(req.getDescription()); p.setPrice(req.getPrice());
        p.setMrp(req.getMrp()); p.setBadge(req.getBadge());
        p.setImageUrl(req.getImageUrl()); p.setFeatured(req.isFeatured());
        if (req.getStockQty() != null) p.setStockQty(req.getStockQty());
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
}
