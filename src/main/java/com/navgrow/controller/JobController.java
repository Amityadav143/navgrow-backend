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

@RestController @RequestMapping("/jobs") @RequiredArgsConstructor
public class JobController {
    private final JobListingRepository jobRepo;
    private final JobApplicationRepository appRepo;

    @Data public static class JobReq {
        @NotBlank String title, department, location;
        String jobType, experience;
        @NotBlank String description;
        List<String> skills;
        JobStatus status;
    }

    @Data public static class ApplicationReq {
        @NotBlank String name;
        @Email @NotBlank String email;
        @NotBlank String phone;
        String experience, coverNote;
    }

    @GetMapping public ResponseEntity<List<JobListing>> listOpen() { return ResponseEntity.ok(jobRepo.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN)); }

    @GetMapping("/{id}") public ResponseEntity<JobListing> get(@PathVariable UUID id) {
        return ResponseEntity.ok(jobRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job", id.toString())));
    }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JobListing> create(@Valid @RequestBody JobReq req) {
        JobListing j = JobListing.builder().title(req.getTitle()).department(req.getDepartment())
            .jobType(req.getJobType() != null ? req.getJobType() : "Full-time")
            .location(req.getLocation()).experience(req.getExperience())
            .description(req.getDescription()).skills(req.getSkills())
            .status(req.getStatus() != null ? req.getStatus() : JobStatus.OPEN).build();
        return ResponseEntity.status(201).body(jobRepo.save(j));
    }

    @PostMapping("/{jobId}/apply")
    public ResponseEntity<Map<String,String>> apply(@PathVariable UUID jobId, @Valid @RequestBody ApplicationReq req) {
        JobListing job = jobRepo.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));
        if (job.getStatus() != JobStatus.OPEN) throw new com.navgrow.exception.BadRequestException("This position is no longer accepting applications.");
        JobApplication app = JobApplication.builder()
            .job(job).jobTitle(job.getTitle())
            .name(req.getName()).email(req.getEmail()).phone(req.getPhone())
            .experience(req.getExperience()).coverNote(req.getCoverNote()).build();
        appRepo.save(app);
        return ResponseEntity.status(201).body(Map.of("message","Application submitted. We'll reach out within 5 business days."));
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