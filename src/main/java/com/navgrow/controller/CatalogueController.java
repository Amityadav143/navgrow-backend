/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import com.navgrow.entity.CatalogueLead;
import com.navgrow.enums.LeadStatus;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.CatalogueLeadRepository;
import com.navgrow.service.AuditService;
import com.navgrow.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Catalogue download with lead capture.
 *
 * The public flow: a visitor submits {@code POST /catalogue/leads} with their
 * name, mobile, email and requirement. We store the lead (visible in
 * Admin → Catalogue Leads), email the visitor a copy and notify the office, then
 * return the download URL. The file itself is streamed from
 * {@code GET /catalogue/download} (served from the packaged PDF).
 */
@RestController
@RequestMapping("/catalogue")
@RequiredArgsConstructor
@Slf4j
public class CatalogueController {

    private final CatalogueLeadRepository repo;
    private final EmailService emailService;
    private final AuditService audit;

    private static final String FILE_PATH = "static/catalogue/Navgrow_Company_Profile_2026.pdf";
    private static final String DOWNLOAD_NAME = "Navgrow_Company_Profile_2026.pdf";

    @Data
    public static class LeadReq {
        @NotBlank @Size(max = 120)
        String name;

        @NotBlank
        @Pattern(regexp = "^[0-9+\\-()\\s]{7,20}$", message = "Please enter a valid mobile number")
        String mobile;

        @Email @NotBlank @Size(max = 160)
        String email;

        @NotBlank @Size(max = 2000, message = "Requirement is too long")
        String requirement;

        @Size(max = 160) String company;
        @Size(max = 120) String city;
        @Size(max = 80)  String catalogueKey;
        @Size(max = 120) String source;
    }

    /** Public: capture the lead, notify, and return the download URL. */
    @PostMapping("/leads")
    public ResponseEntity<Map<String, String>> capture(@Valid @RequestBody LeadReq req,
                                                       HttpServletRequest http) {
        CatalogueLead lead = CatalogueLead.builder()
                .name(req.getName().trim())
                .mobile(req.getMobile().trim())
                .email(req.getEmail().trim())
                .requirement(req.getRequirement().trim())
                .company(req.getCompany())
                .city(req.getCity())
                .catalogueKey(req.getCatalogueKey() != null ? req.getCatalogueKey() : "company-profile-2026")
                .source(req.getSource() != null ? req.getSource() : "website")
                .ipAddress(clientIp(http))
                .userAgent(truncate(http.getHeader("User-Agent"), 500))
                .status(LeadStatus.NEW)
                .build();
        CatalogueLead saved = repo.save(lead);

        // Best-effort notifications — never block the download on email issues.
        try {
            emailService.sendCatalogueToLead(saved.getEmail(), saved.getName());
            emailService.sendCatalogueLeadNotification(saved);
        } catch (Exception e) {
            log.warn("Catalogue lead email failed for {}: {}", saved.getEmail(), e.getMessage());
        }
        audit.log("CATALOGUE_LEAD", "CatalogueLead", saved.getId().toString(),
                  saved.getName() + " — " + saved.getEmail());

        return ResponseEntity.status(201).body(Map.of(
                "message", "Thank you! Your catalogue is downloading, and we've emailed you a copy.",
                "reference", saved.getId().toString(),
                "downloadUrl", "/api/catalogue/download"));
    }

    /** Public: stream the catalogue PDF. */
    @GetMapping("/download")
    public ResponseEntity<Resource> download() {
        Resource pdf = new ClassPathResource(FILE_PATH);
        if (!pdf.exists()) {
            log.error("Catalogue file missing at classpath:{}", FILE_PATH);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + DOWNLOAD_NAME + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(pdf);
    }

    // ─────────────────────────── Admin ───────────────────────────

    @GetMapping("/leads")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<CatalogueLead>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LeadStatus status) {
        Pageable p = PageRequest.of(
                com.navgrow.util.PageUtil.safePage(page),
                com.navgrow.util.PageUtil.safeSize(size),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(status != null
                ? repo.findByStatusOrderByCreatedAtDesc(status, p)
                : repo.findAllByOrderByCreatedAtDesc(p));
    }

    @GetMapping("/leads/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "total", repo.count(),
                "new", repo.countByStatus(LeadStatus.NEW),
                "contacted", repo.countByStatus(LeadStatus.CONTACTED),
                "converted", repo.countByStatus(LeadStatus.CONVERTED),
                "last7Days", repo.countByCreatedAtAfter(LocalDateTime.now().minusDays(7))));
    }

    @PatchMapping("/leads/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<CatalogueLead> updateStatus(@PathVariable UUID id,
                                                      @RequestParam LeadStatus status,
                                                      @RequestParam(required = false) String assignedTo,
                                                      @RequestParam(required = false) String notes) {
        CatalogueLead lead = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogueLead", id.toString()));
        lead.setStatus(status);
        if (assignedTo != null) lead.setAssignedTo(assignedTo);
        if (notes != null) lead.setAdminNotes(notes);
        return ResponseEntity.ok(repo.save(lead));
    }

    @DeleteMapping("/leads/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("CatalogueLead", id.toString());
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
