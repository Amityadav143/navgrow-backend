package com.navgrow.controller;
import com.navgrow.entity.Tender;
import com.navgrow.enums.TenderStatus;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.TenderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/tenders") @RequiredArgsConstructor
public class TenderController {
    private final TenderRepository repo;

    @Data public static class TenderReq {
        @NotBlank String refNumber, title;
        String description;
        BigDecimal valueMin, valueMax;
        LocalDate deadline;
        TenderStatus status;
        boolean featured;
    }

    @GetMapping public ResponseEntity<List<Tender>> listOpen() { return ResponseEntity.ok(repo.findByStatusOrderByDeadlineAsc(TenderStatus.OPEN)); }
    @GetMapping("/featured") public ResponseEntity<List<Tender>> featured() { return ResponseEntity.ok(repo.findByFeaturedTrueAndStatusOrderByDeadlineAsc(TenderStatus.OPEN)); }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Tender> create(@Valid @RequestBody TenderReq req) {
        Tender t = Tender.builder().refNumber(req.getRefNumber()).title(req.getTitle())
            .description(req.getDescription()).valueMin(req.getValueMin()).valueMax(req.getValueMax())
            .deadline(req.getDeadline() != null ? req.getDeadline() : LocalDate.now().plusMonths(1))
            .status(req.getStatus() != null ? req.getStatus() : TenderStatus.OPEN)
            .featured(req.isFeatured()).build();
        return ResponseEntity.status(201).body(repo.save(t));
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Tender> update(@PathVariable UUID id, @Valid @RequestBody TenderReq req) {
        Tender t = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        t.setTitle(req.getTitle()); t.setDescription(req.getDescription());
        t.setValueMin(req.getValueMin()); t.setValueMax(req.getValueMax());
        t.setDeadline(req.getDeadline()); t.setFeatured(req.isFeatured());
        if (req.getStatus() != null) t.setStatus(req.getStatus());
        return ResponseEntity.ok(repo.save(t));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { repo.deleteById(id); return ResponseEntity.noContent().build(); }
}