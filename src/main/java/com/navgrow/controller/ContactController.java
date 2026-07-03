/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.ContactMessage;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.ContactMessageRepository;
import com.navgrow.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/contact")
@RequiredArgsConstructor
public class ContactController {
    private final ContactMessageRepository repo;
    private final EmailService emailService;

    @Data
    public static class ContactRequest {
        @NotBlank String name;
        @Email @NotBlank String email;
        String phone;
        String company;
        @NotBlank String subject;
        @NotBlank @Size(min=10) String message;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@Valid @RequestBody ContactRequest req) {
        ContactMessage msg = ContactMessage.builder()
            .name(req.getName()).email(req.getEmail()).phone(req.getPhone())
            .company(req.getCompany()).subject(req.getSubject()).message(req.getMessage())
            .build();
        repo.save(msg);
        emailService.sendContactNotification(req.getName(), req.getEmail(), req.getSubject(), req.getMessage());
        return ResponseEntity.status(201).body(Map.of("message", "Message received. We'll respond within 24 hours."));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<ContactMessage>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unread) {
        Pageable pageable = PageRequest.of(com.navgrow.util.PageUtil.safePage(page), com.navgrow.util.PageUtil.safeSize(size), Sort.by("createdAt").descending());
        Page<ContactMessage> result = Boolean.TRUE.equals(unread)
            ? repo.findByReadFalseOrderByCreatedAtDesc(pageable)
            : repo.findAllByOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<ContactMessage> markRead(@PathVariable UUID id) {
        ContactMessage msg = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", id.toString()));
        msg.setRead(true);
        return ResponseEntity.ok(repo.save(msg));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", repo.countUnread()));
    }

    @Data
    public static class ReplyRequest {
        @NotBlank String message;
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, String>> reply(
            @PathVariable UUID id,
            @Valid @RequestBody ReplyRequest req) {
        ContactMessage msg = repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContactMessage", id.toString()));
        // Send reply email
        emailService.sendReplyEmail(msg.getEmail(), msg.getName(), msg.getSubject(), req.getMessage());
        // Mark as read
        msg.setRead(true);
        repo.save(msg);
        return ResponseEntity.ok(Map.of("message", "Reply sent successfully."));
    }
}
