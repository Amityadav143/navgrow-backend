/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.NewsArticle;
import com.navgrow.enums.NewsStatus;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.NewsArticleRepository;
import com.navgrow.util.SlugUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/news") @RequiredArgsConstructor
public class NewsController {
    private final NewsArticleRepository repo;
    private final SlugUtil slugUtil;

    @Data public static class NewsReq {
        @NotBlank String title, content;
        String excerpt, category, imageUrl, authorName;
        List<String> tags;
        NewsStatus status;
    }

    @GetMapping public ResponseEntity<Page<NewsArticle>> list(
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="12") int size,
            @RequestParam(required=false) String category,
            @RequestParam(required=false) String q,
            @RequestParam(required=false) NewsStatus status) {
        // Default to PUBLISHED for public endpoint; admin can pass DRAFT/ALL
        NewsStatus effectiveStatus = status != null ? status : NewsStatus.PUBLISHED;
        Pageable p = PageRequest.of(page, size, Sort.by("publishedAt").descending());
        if (q != null && !q.isBlank()) {
            // Text search across title and excerpt
            return ResponseEntity.ok(repo.findByStatusAndTitleContainingIgnoreCaseOrStatusAndExcerptContainingIgnoreCase(
                effectiveStatus, q, effectiveStatus, q, p));
        }
        return ResponseEntity.ok(category != null
            ? repo.findByCategoryAndStatusOrderByPublishedAtDesc(category, effectiveStatus, p)
            : repo.findByStatusOrderByPublishedAtDesc(effectiveStatus, p));
    }

    @GetMapping("/{slug}") public ResponseEntity<NewsArticle> get(@PathVariable String slug) {
        NewsArticle a = repo.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Article", slug));
        a.setViewCount(a.getViewCount() + 1);
        return ResponseEntity.ok(repo.save(a));
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<NewsArticle> create(@Valid @RequestBody NewsReq req) {
        String slug = slugUtil.uniqueSlug(req.getTitle(), s -> repo.findBySlug(s).isPresent());
        NewsStatus st = req.getStatus() != null ? req.getStatus() : NewsStatus.DRAFT;
        NewsArticle a = NewsArticle.builder().title(req.getTitle()).slug(slug)
            .excerpt(req.getExcerpt()).content(req.getContent()).category(req.getCategory())
            .imageUrl(req.getImageUrl()).authorName(req.getAuthorName() != null ? req.getAuthorName() : "Navgrow Team")
            .tags(req.getTags()).status(st)
            .publishedAt(st == NewsStatus.PUBLISHED ? LocalDateTime.now() : null).build();
        return ResponseEntity.status(201).body(repo.save(a));
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<NewsArticle> update(@PathVariable UUID id, @Valid @RequestBody NewsReq req) {
        NewsArticle a = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Article", id.toString()));
        a.setTitle(req.getTitle()); a.setContent(req.getContent()); a.setExcerpt(req.getExcerpt());
        a.setCategory(req.getCategory()); a.setImageUrl(req.getImageUrl()); a.setTags(req.getTags());
        if (req.getStatus() != null) {
            a.setStatus(req.getStatus());
            if (req.getStatus() == NewsStatus.PUBLISHED && a.getPublishedAt() == null) a.setPublishedAt(LocalDateTime.now());
        }
        return ResponseEntity.ok(repo.save(a));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { repo.deleteById(id); return ResponseEntity.noContent().build(); }
}