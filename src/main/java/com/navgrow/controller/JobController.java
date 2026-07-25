/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.*;
import com.navgrow.enums.*;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@lombok.extern.slf4j.Slf4j
@RestController @RequestMapping("/jobs") @RequiredArgsConstructor
public class JobController {
    private final JobListingRepository jobRepo;
    private final JobApplicationRepository appRepo;
    private final com.navgrow.service.AuditService audit;
    private final com.navgrow.service.EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Data public static class JobReq {
        @NotBlank String title, department, location;
        String jobType, experience;
        @NotBlank String description;
        List<String> skills;
        JobStatus status;
        java.math.BigDecimal salaryFrom, salaryTo;
        Integer openings;
        java.time.LocalDateTime applicationDeadline;
        /** Admin UI convenience flag — maps to status OPEN/CLOSED when status is absent. */
        Boolean active;

        JobStatus resolvedStatus(JobStatus fallback) {
            if (status != null) return status;
            if (active != null) return active ? JobStatus.OPEN : JobStatus.CLOSED;
            return fallback;
        }
    }

    @Data public static class ApplicationReq {
        @NotBlank String name;
        @Email @NotBlank String email;
        @NotBlank String phone;
        String experience, coverNote;
        /** URL returned by /jobs/resume — the entity has always had the column,
         *  but the request never carried it, so no CV could reach an application. */
        String resumeUrl;
    }

    @GetMapping public ResponseEntity<List<JobListing>> listOpen() { return ResponseEntity.ok(jobRepo.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN)); }

    /** Admin listing — returns jobs in every status so closed/paused roles stay manageable. */
    @GetMapping("/manage") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<JobListing>> listAll() {
        return ResponseEntity.ok(jobRepo.findAll(org.springframework.data.domain.Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}") public ResponseEntity<JobListing> get(@PathVariable UUID id) {
        return ResponseEntity.ok(jobRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job", id.toString())));
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobListing> create(@Valid @RequestBody JobReq req) {
        JobListing j = JobListing.builder().title(req.getTitle()).department(req.getDepartment())
            .jobType(req.getJobType() != null ? req.getJobType() : "Full-time")
            .location(req.getLocation()).experience(req.getExperience())
            .description(req.getDescription()).skills(req.getSkills())
            .salaryFrom(req.getSalaryFrom()).salaryTo(req.getSalaryTo())
            .openings(req.getOpenings() != null && req.getOpenings() > 0 ? req.getOpenings() : 1)
            .applicationDeadline(req.getApplicationDeadline())
            .status(req.resolvedStatus(JobStatus.OPEN)).build();
        JobListing saved = jobRepo.save(j);
        audit.log("JOB_CREATE", "JobListing", saved.getId().toString(), saved.getTitle());
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobListing> update(@PathVariable UUID id, @Valid @RequestBody JobReq req) {
        JobListing j = jobRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
        j.setTitle(req.getTitle()); j.setDepartment(req.getDepartment());
        j.setLocation(req.getLocation());
        if (req.getJobType()    != null) j.setJobType(req.getJobType());
        if (req.getExperience() != null) j.setExperience(req.getExperience());
        j.setDescription(req.getDescription());
        if (req.getSkills()     != null) j.setSkills(req.getSkills());
        if (req.getSalaryFrom() != null) j.setSalaryFrom(req.getSalaryFrom());
        if (req.getSalaryTo()   != null) j.setSalaryTo(req.getSalaryTo());
        if (req.getOpenings()   != null && req.getOpenings() > 0) j.setOpenings(req.getOpenings());
        if (req.getApplicationDeadline() != null) j.setApplicationDeadline(req.getApplicationDeadline());
        j.setStatus(req.resolvedStatus(j.getStatus()));
        JobListing saved = jobRepo.save(j);
        audit.log("JOB_UPDATE", "JobListing", saved.getId().toString(), saved.getTitle());
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/toggle") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobListing> toggle(@PathVariable UUID id) {
        JobListing j = jobRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
        j.setStatus(j.getStatus() == JobStatus.OPEN ? JobStatus.CLOSED : JobStatus.OPEN);
        JobListing saved = jobRepo.save(j);
        audit.log("JOB_TOGGLE", "JobListing", saved.getId().toString(),
                  saved.getTitle() + " -> " + saved.getStatus());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobRepo.deleteById(id);
        audit.log("JOB_DELETE", "JobListing", id.toString(), null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{jobId}/apply")
    public ResponseEntity<Map<String,String>> apply(@PathVariable UUID jobId, @Valid @RequestBody ApplicationReq req) {
        JobListing job = jobRepo.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));
        if (job.getStatus() != JobStatus.OPEN) throw new com.navgrow.exception.BadRequestException("This position is no longer accepting applications.");
        JobApplication app = JobApplication.builder()
            .job(job).jobTitle(job.getTitle())
            .name(req.getName()).email(req.getEmail()).phone(req.getPhone())
            .experience(req.getExperience()).coverNote(req.getCoverNote())
            .resumeUrl(req.getResumeUrl()).build();
        appRepo.save(app);
        audit.log("JOB_APPLICATION", "JobApplication", app.getId() != null ? app.getId().toString() : null,
                  req.getName() + " -> " + job.getTitle());
        // Best-effort notification: a failed mail must not lose the application.
        try {
            emailService.sendJobApplicationReceived(app);
        } catch (Exception e) {
            log.warn("Job application email failed for {}: {}", req.getEmail(), e.getMessage());
        }
        return ResponseEntity.status(201).body(Map.of("message","Application submitted. We'll reach out within 5 business days."));
    }

    /**
     * Accepts a CV from an applicant and returns a URL to attach to the
     * application. Deliberately separate from the admin uploader: candidates are
     * anonymous, so this is limited to document types and a small size cap.
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String,Object>> uploadResume(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty())
            throw new com.navgrow.exception.BadRequestException("Please choose a file to upload.");
        if (file.getSize() > 5L * 1024 * 1024)
            throw new com.navgrow.exception.BadRequestException("Your CV must be under 5 MB.");

        String original = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String ext = original.contains(".")
            ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!java.util.Set.of("pdf", "doc", "docx").contains(ext))
            throw new com.navgrow.exception.BadRequestException("Please upload your CV as a PDF or Word document.");

        // Read once, then verify the bytes actually match the extension.
        byte[] bytes = file.getBytes();
        if (bytes.length < 4) throw new com.navgrow.exception.BadRequestException("That file appears to be empty.");
        String magic = new String(java.util.Arrays.copyOfRange(bytes, 0, 4), java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean looksPdf  = magic.startsWith("%PDF");
        boolean looksZip  = bytes[0] == 0x50 && bytes[1] == 0x4B;              // docx
        boolean looksDoc  = (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF; // legacy .doc
        if (!(looksPdf || looksZip || looksDoc))
            throw new com.navgrow.exception.BadRequestException("That file does not look like a PDF or Word document.");

        java.nio.file.Path dir = java.nio.file.Paths.get(uploadDir, "resumes").toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(dir);
        String stored = java.util.UUID.randomUUID() + "." + ext;
        java.nio.file.Path target = dir.resolve(stored).normalize();
        if (!target.startsWith(dir)) throw new com.navgrow.exception.BadRequestException("Invalid file name.");
        java.nio.file.Files.write(target, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);

        String url = org.springframework.web.servlet.support.ServletUriComponentsBuilder
            .fromCurrentContextPath().path("/uploads/resumes/").path(stored).toUriString();
        log.info("Resume uploaded: {} ({} bytes)", original, bytes.length);
        return ResponseEntity.ok(Map.of("url", url, "fileName", stored, "originalName", original, "size", bytes.length));
    }

    @GetMapping("/{jobId}/applications") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<JobApplication>> getApplications(@PathVariable UUID jobId,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(appRepo.findByJobIdOrderByCreatedAtDesc(jobId, PageRequest.of(page, size)));
    }

    @PatchMapping("/applications/{id}/status") @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<JobApplication> updateAppStatus(@PathVariable UUID id, @RequestParam ApplicationStatus status) {
        JobApplication app = appRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application", id.toString()));
        app.setStatus(status);
        return ResponseEntity.ok(appRepo.save(app));
    }
}