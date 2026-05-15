package com.navgrow.controller;
import com.navgrow.entity.User;
import com.navgrow.exception.BadRequestException;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/users") @RequiredArgsConstructor
public class UserController {
    private final UserRepository repo;
    private final PasswordEncoder encoder;

    @Data public static class ProfileUpdate {
        String fullName;
        String phone;
        String company;
        String city;
        String state;
        String pincode;
    }

    @Data public static class PasswordChange {
        @NotBlank String currentPassword;
        @NotBlank @Size(min=8) String newPassword;
    }

    @GetMapping("/me")
    public ResponseEntity<User> profile(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(repo.findByEmail(ud.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    @PutMapping("/me")
    public ResponseEntity<User> updateProfile(@AuthenticationPrincipal UserDetails ud, @Valid @RequestBody ProfileUpdate req) {
        User user = repo.findByEmail(ud.getUsername()).orElseThrow();
        if (req.getFullName() != null) user.setFullName(req.getFullName());
        if (req.getPhone()    != null) user.setPhone(req.getPhone());
        return ResponseEntity.ok(repo.save(user));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Map<String,String>> changePassword(@AuthenticationPrincipal UserDetails ud, @Valid @RequestBody PasswordChange req) {
        User user = repo.findByEmail(ud.getUsername()).orElseThrow();
        if (!encoder.matches(req.getCurrentPassword(), user.getPasswordHash()))
            throw new BadRequestException("Current password is incorrect.");
        user.setPasswordHash(encoder.encode(req.getNewPassword()));
        repo.save(user);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<User>> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(repo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
}
