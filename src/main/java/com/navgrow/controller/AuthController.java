/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 · navgrow.org
 */
package com.navgrow.controller;

import com.navgrow.entity.User;
import com.navgrow.enums.UserRole;
import com.navgrow.exception.BadRequestException;
import com.navgrow.repository.UserRepository;
import com.navgrow.security.JwtUtil;
import com.navgrow.service.impl.UserDetailsServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authManager;
    private final UserDetailsServiceImpl uds;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    // ── DTOs ────────────────────────────────────────────────────────────────
    @Data
    public static class LoginRequest {
        @NotBlank String email;       // allows email OR phone
        @NotBlank String password;
    }

    @Data
    public static class PhoneLoginRequest {
        @NotBlank String phone;
        @NotBlank String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank String fullName;
        String email;
        @Size(min = 8) @NotBlank String password;
        String phone;
    }

    // ── Shared token-response builder ────────────────────────────────────────
    private Map<String, Object> buildTokenResponse(String emailOrPhone, UserDetails ud) {
        String token   = jwtUtil.generateToken(ud);
        String refresh = jwtUtil.generateRefreshToken(ud);

        // Fetch full user info for response
        Optional<User> userOpt = userRepo.findByEmail(emailOrPhone)
            .or(() -> userRepo.findByPhone(emailOrPhone));
        String fullName = userOpt.map(User::getFullName).orElse("");
        String avatarUrl = userOpt.map(User::getAvatarUrl).orElse("");

        userOpt.ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            userRepo.save(u);
        });

        return Map.of(
            "accessToken",  token,
            "refreshToken", refresh,
            "tokenType",    "Bearer",
            "email",        ud.getUsername(),
            "fullName",     fullName,
            "avatarUrl",    avatarUrl != null ? avatarUrl : "",
            "roles",        ud.getAuthorities()
        );
    }

    // ── Email login ──────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid email or password"));
        }
        UserDetails ud = uds.loadUserByUsername(req.getEmail());
        return ResponseEntity.ok(buildTokenResponse(req.getEmail(), ud));
    }

    // ── Phone login — FIX: now properly implemented ─────────────────────────
    @PostMapping("/login-with-phone")
    public ResponseEntity<?> loginWithPhone(@Valid @RequestBody PhoneLoginRequest req) {
        User user = userRepo.findByPhone(req.getPhone())
            .orElse(null);
        if (user == null || !encoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid phone number or password"));
        }
        UserDetails ud = uds.loadUserByUsername(user.getEmail());
        return ResponseEntity.ok(buildTokenResponse(req.getPhone(), ud));
    }

    // ── Register ─────────────────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (req.getEmail() == null && req.getPhone() == null) {
            throw new BadRequestException("Email or phone number is required.");
        }
        if (req.getEmail() != null && userRepo.existsByEmail(req.getEmail())) {
            throw new BadRequestException("Email already registered.");
        }
        if (req.getPhone() != null && userRepo.findByPhone(req.getPhone()).isPresent()) {
            throw new BadRequestException("Phone number already registered.");
        }

        String emailKey = req.getEmail() != null ? req.getEmail()
                : req.getPhone() + "@phone.navgrow.local"; // synthetic email for phone-only users

        User user = User.builder()
            .fullName(req.getFullName())
            .email(emailKey)
            .passwordHash(encoder.encode(req.getPassword()))
            .phone(req.getPhone())
            .role(UserRole.USER)
            .active(true)
            .build();
        userRepo.save(user);
        log.info("New user registered: {}", emailKey);
        return ResponseEntity.status(201)
            .body(Map.of("message", "Registration successful. Please log in."));
    }

    // ── Refresh token ────────────────────────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestParam String refreshToken) {
        try {
            String email = jwtUtil.extractEmail(refreshToken);
            UserDetails ud = uds.loadUserByUsername(email);
            String newToken = jwtUtil.generateToken(ud);
            return ResponseEntity.ok(Map.of("accessToken", newToken, "tokenType", "Bearer"));
        } catch (Exception e) {
            log.warn("Refresh token invalid: {}", e.getMessage());
            throw new BadRequestException("Invalid or expired refresh token.");
        }
    }

    // ── Logout (client-side, but record server-side) ─────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Stateless JWT — actual invalidation happens on client
        // Future: add token blacklist if needed
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }
}
