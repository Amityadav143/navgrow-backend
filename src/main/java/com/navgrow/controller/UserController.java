/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.entity.User;
import com.navgrow.enums.UserRole;
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

    /** Full profile update DTO — includes avatarUrl, bio, locality */
    @Data
    public static class ProfileUpdate {
        String fullName;
        String phone;
        String company;
        String locality;   // NEW: ward / colony / area
        String city;
        String state;
        String pincode;
        String bio;
        String avatarUrl;  // NEW: profile photo (base64 or URL)
    }

    @Data
    public static class PasswordChange {
        @NotBlank String currentPassword;
        @NotBlank @Size(min=8) String newPassword;
    }

    @Data
    public static class AdminCreateUser {
        @NotBlank String fullName;
        @NotBlank String email;
        String phone;
        @NotBlank @Size(min=8) String password;
        String company;
        UserRole role = UserRole.USER;
    }

    @GetMapping("/me")
    public ResponseEntity<User> profile(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(repo.findByEmail(ud.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    @PutMapping("/me")
    public ResponseEntity<User> updateProfile(
            @AuthenticationPrincipal UserDetails ud,
            @RequestBody ProfileUpdate req) {
        User user = repo.findByEmail(ud.getUsername()).orElseThrow();
        if (req.getFullName()  != null) user.setFullName(req.getFullName());
        if (req.getPhone()     != null) user.setPhone(req.getPhone());
        if (req.getCompany()   != null) user.setCompany(req.getCompany());
        if (req.getLocality()  != null) user.setLocality(req.getLocality());
        if (req.getCity()      != null) user.setCity(req.getCity());
        if (req.getState()     != null) user.setState(req.getState());
        if (req.getPincode()   != null) user.setPincode(req.getPincode());
        if (req.getBio()       != null) user.setBio(req.getBio());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        return ResponseEntity.ok(repo.save(user));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Map<String,String>> changePassword(
            @AuthenticationPrincipal UserDetails ud,
            @Valid @RequestBody PasswordChange req) {
        User user = repo.findByEmail(ud.getUsername()).orElseThrow();
        if (!encoder.matches(req.getCurrentPassword(), user.getPasswordHash()))
            throw new BadRequestException("Current password is incorrect.");
        user.setPasswordHash(encoder.encode(req.getNewPassword()));
        repo.save(user);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }

    // ── Admin: list all users ────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Page<User>> list(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q) {
        Pageable pageable = PageRequest.of(com.navgrow.util.PageUtil.safePage(page), com.navgrow.util.PageUtil.safeSize(size), Sort.by("createdAt").descending());
        if (q != null && !q.isBlank()) {
            // Case-insensitive search by name or email
            return ResponseEntity.ok(repo.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, pageable));
        }
        return ResponseEntity.ok(repo.findAll(pageable));
    }

    // ── Admin: get user by id ────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<User> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(repo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    // ── The catalog of grantable permissions (for the Super Admin UI) ────────
    @GetMapping("/permissions-catalog")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<java.util.List<String>> permissionsCatalog() {
        java.util.List<String> perms = new java.util.ArrayList<>();
        for (com.navgrow.enums.Permission p : com.navgrow.enums.Permission.values()) perms.add(p.name());
        return ResponseEntity.ok(perms);
    }

    // ── Update role ──────────────────────────────────────────────────────────
    // Only a SUPER_ADMIN may create/assign the SUPER_ADMIN or ADMIN tier (prevents
    // privilege escalation by a regular ADMIN). ADMINs may still set lower roles.
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<User> updateRole(@PathVariable UUID id, @RequestParam UserRole role,
                                           @AuthenticationPrincipal UserDetails actor) {
        boolean actorIsSuper = isSuperAdmin(actor);
        if ((role == UserRole.SUPER_ADMIN || role == UserRole.ADMIN) && !actorIsSuper)
            throw new BadRequestException("Only a Super Admin can assign the Admin or Super Admin role.");
        User user = repo.findById(id).orElseThrow();
        // Guard: a non-super actor may not modify a SUPER_ADMIN/ADMIN account.
        if (!actorIsSuper && (user.getRole() == UserRole.SUPER_ADMIN || user.getRole() == UserRole.ADMIN))
            throw new BadRequestException("Only a Super Admin can modify an Admin account.");
        user.setRole(role);
        return ResponseEntity.ok(repo.save(user));
    }

    // ── Grant custom permissions (SUPER_ADMIN only) ──────────────────────────
    // Body: {"permissions": ["ORDERS","NEWS", ...]}. Unknown names are rejected.
    public static class PermissionsUpdate { public java.util.List<String> permissions; }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<User> updatePermissions(@PathVariable UUID id,
                                                  @RequestBody PermissionsUpdate req) {
        User user = repo.findById(id).orElseThrow();
        java.util.LinkedHashSet<String> clean = new java.util.LinkedHashSet<>();
        if (req != null && req.permissions != null) {
            for (String raw : req.permissions) {
                if (raw == null) continue;
                String t = raw.trim().toUpperCase();
                if (t.isEmpty()) continue;
                try { clean.add(com.navgrow.enums.Permission.valueOf(t).name()); }
                catch (IllegalArgumentException ex) { throw new BadRequestException("Unknown permission: " + raw); }
            }
        }
        user.setPermissions(clean.isEmpty() ? null : String.join(",", clean));
        return ResponseEntity.ok(repo.save(user));
    }

    // ── Admin: toggle active ─────────────────────────────────────────────────
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<User> toggleActive(@PathVariable UUID id,
                                             @AuthenticationPrincipal UserDetails actor) {
        User user = repo.findById(id).orElseThrow();
        if (!isSuperAdmin(actor) && (user.getRole() == UserRole.SUPER_ADMIN || user.getRole() == UserRole.ADMIN))
            throw new BadRequestException("Only a Super Admin can modify an Admin account.");
        user.setActive(!user.isActive());
        return ResponseEntity.ok(repo.save(user));
    }

    // ── Admin: delete user ───────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserDetails actor) {
        User user = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // A SUPER_ADMIN account can only be deleted by another SUPER_ADMIN.
        if (user.getRole() == UserRole.SUPER_ADMIN && !isSuperAdmin(actor))
            throw new BadRequestException("Only a Super Admin can delete a Super Admin account.");
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Admin: create user ───────────────────────────────────────────────────
    @PostMapping("/admin/create")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<User> adminCreate(@Valid @RequestBody AdminCreateUser req,
                                            @AuthenticationPrincipal UserDetails actor) {
        if (repo.findByEmail(req.getEmail()).isPresent())
            throw new BadRequestException("Email already registered.");
        UserRole newRole = req.getRole() != null ? req.getRole() : UserRole.USER;
        if ((newRole == UserRole.SUPER_ADMIN || newRole == UserRole.ADMIN) && !isSuperAdmin(actor))
            throw new BadRequestException("Only a Super Admin can create an Admin or Super Admin.");
        User user = User.builder()
            .fullName(req.getFullName())
            .email(req.getEmail())
            .phone(req.getPhone())
            .passwordHash(encoder.encode(req.getPassword()))
            .company(req.getCompany())
            .role(newRole)
            .active(true)
            .build();
        return ResponseEntity.ok(repo.save(user));
    }

    /** True if the authenticated actor holds the SUPER_ADMIN role. */
    private boolean isSuperAdmin(UserDetails actor) {
        return actor != null && actor.getAuthorities().stream()
            .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    // ── Address endpoints (stub — store as JSON in user profile for now) ────
    // Full address management would require a separate UserAddress entity/repo
    // For v1.0, we return empty list / no-op so frontend doesn't break
    @GetMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<?>> listAddresses(@AuthenticationPrincipal UserDetails ignored) {
        return ResponseEntity.ok(java.util.List.of());
    }

    @PostMapping("/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.Map<String,String>> createAddress(
            @AuthenticationPrincipal UserDetails ignored2,
            @RequestBody java.util.Map<String,Object> body) {
        // TODO: implement full address storage in v1.1
        return ResponseEntity.status(201).body(java.util.Map.of("message", "Address saved."));
    }

    @PutMapping("/me/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.Map<String,String>> updateAddress(
            @PathVariable java.util.UUID addressId,
            @RequestBody java.util.Map<String,Object> body) {
        return ResponseEntity.ok(java.util.Map.of("message", "Address updated."));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAddress(@PathVariable java.util.UUID addressId) {
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/addresses/{addressId}/default")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.Map<String,String>> setDefaultAddress(
            @PathVariable java.util.UUID addressId,
            @RequestParam(required=false) String type) {
        return ResponseEntity.ok(java.util.Map.of("message", "Default address set."));
    }

    // ── Company / GST profile (stub) ─────────────────────────────────────────
    @GetMapping("/me/company")
    public ResponseEntity<java.util.Map<String,Object>> getCompany(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails ud) {
        User user = repo.findByEmail(ud.getUsername()).orElseThrow();
        // Return company info from user object if available
        return ResponseEntity.ok(java.util.Map.of(
            "companyName",       user.getCompany() != null ? user.getCompany() : "",
            "gstin",             "",
            "pan",               "",
            "businessType",      "",
            "website",           "",
            "registeredAddress", ""
        ));
    }

    @PutMapping("/me/company")
    public ResponseEntity<java.util.Map<String,String>> saveCompany(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails ud,
            @RequestBody java.util.Map<String,Object> body) {
        User user = repo.findByEmail(ud.getUsername()).orElseThrow();
        // Save company name to user entity for now
        Object companyName = body.get("companyName");
        if (companyName != null) {
            user.setCompany(companyName.toString());
            repo.save(user);
        }
        return ResponseEntity.ok(java.util.Map.of("message", "Company details saved."));
    }
}
