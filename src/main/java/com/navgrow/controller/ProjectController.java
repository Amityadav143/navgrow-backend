package com.navgrow.controller;
import com.navgrow.entity.Project;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.ProjectRepository;
import com.navgrow.util.SlugUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/projects") @RequiredArgsConstructor
public class ProjectController {
    private final ProjectRepository repo;
    private final SlugUtil slugUtil;

    @Data public static class ProjectReq {
        @NotBlank String title, category;
        String client, location, year, description, imageUrl;
        boolean featured;
        int sortOrder;
    }

    @GetMapping public ResponseEntity<List<Project>> list() { return ResponseEntity.ok(repo.findAllByOrderBySortOrderAscCreatedAtDesc()); }
    @GetMapping("/featured") public ResponseEntity<List<Project>> featured() { return ResponseEntity.ok(repo.findByFeaturedTrueOrderBySortOrderAsc()); }

    @GetMapping("/{slug}") public ResponseEntity<Project> get(@PathVariable String slug) {
        return ResponseEntity.ok(repo.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Project", slug)));
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Project> create(@Valid @RequestBody ProjectReq req) {
        String slug = slugUtil.uniqueSlug(req.getTitle(), s -> repo.findBySlug(s).isPresent());
        Project p = Project.builder().title(req.getTitle()).slug(slug).category(req.getCategory())
            .client(req.getClient()).location(req.getLocation()).year(req.getYear())
            .description(req.getDescription()).imageUrl(req.getImageUrl())
            .featured(req.isFeatured()).sortOrder(req.getSortOrder()).build();
        return ResponseEntity.status(201).body(repo.save(p));
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Project> update(@PathVariable UUID id, @Valid @RequestBody ProjectReq req) {
        Project p = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
        p.setTitle(req.getTitle()); p.setCategory(req.getCategory());
        p.setClient(req.getClient()); p.setLocation(req.getLocation()); p.setYear(req.getYear());
        p.setDescription(req.getDescription()); p.setImageUrl(req.getImageUrl());
        p.setFeatured(req.isFeatured()); p.setSortOrder(req.getSortOrder());
        return ResponseEntity.ok(repo.save(p));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { repo.deleteById(id); return ResponseEntity.noContent().build(); }
}