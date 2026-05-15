package com.navgrow.controller;
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

    @Data public static class GalleryReq {
        @NotBlank String title, imageUrl;
        String category, location, year, altText;
        int sortOrder;
    }

    @GetMapping public ResponseEntity<List<GalleryItem>> list(@RequestParam(required=false) String category) {
        return ResponseEntity.ok(category != null
            ? repo.findByCategoryAndActiveTrueOrderBySortOrderAsc(category)
            : repo.findByActiveTrueOrderBySortOrderAscCreatedAtDesc());
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GalleryItem> create(@Valid @RequestBody GalleryReq req) {
        GalleryItem item = GalleryItem.builder().title(req.getTitle())
            .category(req.getCategory() != null ? req.getCategory() : "Projects")
            .location(req.getLocation()).year(req.getYear())
            .imageUrl(req.getImageUrl()).altText(req.getAltText())
            .sortOrder(req.getSortOrder()).build();
        return ResponseEntity.status(201).body(repo.save(item));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        GalleryItem item = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("GalleryItem", id.toString()));
        item.setActive(false);
        repo.save(item);
        return ResponseEntity.noContent().build();
    }
}