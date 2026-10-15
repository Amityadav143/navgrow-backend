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
import org.springframework.security.core.AuthenticationException;
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
    private final com.navgrow.service.OtpService otpService;
    private final com.navgrow.service.EmailService emailService;
    private final com.navgrow.service.SmsService smsService;

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

        // Fetch full user info for response (ud.getUsername() is the stored email).
        Optional<User> userOpt = userRepo.findByEmail(ud.getUsername())
            .or(() -> userRepo.findByEmail(emailOrPhone))
            .or(() -> findByPhoneLoose(emailOrPhone));
        String fullName = userOpt.map(User::getFullName).orElse("");
        String avatarUrl = userOpt.map(User::getAvatarUrl).orElse("");

        userOpt.ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            userRepo.save(u);
        });

        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("accessToken",  token);
        resp.put("refreshToken", refresh);
        resp.put("tokenType",    "Bearer");
        resp.put("email",        ud.getUsername());
        resp.put("fullName",     fullName);
        resp.put("avatarUrl",    avatarUrl != null ? avatarUrl : "");
        resp.put("roles",        ud.getAuthorities());
        // Explicit base role + the effective custom permissions, so the client can
        // show exactly the admin areas this user is allowed into.
        resp.put("role", userOpt.map(u -> u.getRole().name()).orElse("USER"));
        resp.put("permissions", userOpt.map(User::getEffectivePermissions)
            .orElse(java.util.Collections.emptySet()));
        return resp;
    }

    // ── Email login ──────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String email = req.getEmail().trim();
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.getPassword()));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", DEACTIVATED_MSG));
        } catch (AuthenticationException e) {
            // Bad credentials, unknown user, locked… — one generic message (no account enumeration).
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid email or password"));
        }
        UserDetails ud = uds.loadUserByUsername(email);
        return ResponseEntity.ok(buildTokenResponse(email, ud));
    }

    private static final String DEACTIVATED_MSG =
        "This account has been deactivated. Please contact info@navgrow.org or +91 89270 70972.";

    /**
     * Normalises an Indian mobile number to its 10 digits ("+91 98765-43210" →
     * "9876543210"). Returns the trimmed input unchanged if it isn't one.
     */
    static String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0"))  digits = digits.substring(1);
        return digits.matches("^[6-9]\\d{9}$") ? digits : raw.trim();
    }

    /** Finds a user by phone however it was stored (10 digits, +91…, with spaces). */
    private Optional<User> findByPhoneLoose(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String n = normalizePhone(raw);
        return userRepo.findByPhone(n)
            .or(() -> userRepo.findByPhone(raw.trim()))
            .or(() -> userRepo.findByPhone("+91" + n))
            .or(() -> userRepo.findByPhone("91" + n))
            .or(() -> userRepo.findByPhone("+91 " + n));
    }

    // ── Phone login — FIX: now properly implemented ─────────────────────────
    @PostMapping("/login-with-phone")
    public ResponseEntity<?> loginWithPhone(@Valid @RequestBody PhoneLoginRequest req) {
        User user = findByPhoneLoose(req.getPhone()).orElse(null);
        if (user == null || !encoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid phone number or password"));
        }
        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", DEACTIVATED_MSG));
        }
        UserDetails ud = uds.loadUserByUsername(user.getEmail());
        return ResponseEntity.ok(buildTokenResponse(req.getPhone(), ud));
    }

    // ── OTP login ────────────────────────────────────────────────────────────
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@RequestParam String phone) {
        String p = phone == null ? "" : phone.replaceAll("\\s", "").trim();
        if (!p.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a valid 10-digit mobile number."));
        }
        // Only issue an OTP for a registered phone (avoids enumeration of unknowns via behaviour change:
        // we still return 200 so the response doesn't reveal whether the number exists).
        if (findByPhoneLoose(p).isPresent()) {
            otpService.issue(p);
        }
        return ResponseEntity.ok(Map.of("message", "If the number is registered, an OTP has been sent."));
    }

    @Data
    public static class OtpVerifyRequest {
        @NotBlank String phone;
        @NotBlank String otp;
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest req) {
        String p = req.getPhone() == null ? "" : req.getPhone().replaceAll("\\s", "").trim();
        User user = findByPhoneLoose(p).orElse(null);
        if (user == null || !otpService.verify(p, req.getOtp() == null ? "" : req.getOtp().trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid or expired OTP. Please try again."));
        }
        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", DEACTIVATED_MSG));
        }
        UserDetails ud = uds.loadUserByUsername(user.getEmail());
        return ResponseEntity.ok(buildTokenResponse(p, ud));
    }
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        // Normalise first: blank → absent, emails lower-cased (no "A@x.com" vs
        // "a@x.com" duplicates), phones reduced to their 10 digits.
        if (req.getEmail() != null) req.setEmail(req.getEmail().isBlank() ? null : req.getEmail().trim().toLowerCase());
        if (req.getPhone() != null) req.setPhone(req.getPhone().isBlank() ? null : normalizePhone(req.getPhone()));
        if (req.getEmail() == null && req.getPhone() == null) {
            throw new BadRequestException("Email or phone number is required.");
        }
        if (req.getEmail() != null && !req.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")) {
            throw new BadRequestException("Please enter a valid email address.");
        }
        if (req.getPhone() != null && !req.getPhone().matches("^[6-9]\\d{9}$")) {
            throw new BadRequestException("Please enter a valid 10-digit mobile number.");
        }
        if (req.getEmail() != null && userRepo.existsByEmailIgnoreCase(req.getEmail())) {
            throw new BadRequestException("Email already registered.");
        }
        if (req.getPhone() != null && findByPhoneLoose(req.getPhone()).isPresent()) {
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

        // Welcome the new customer. Only email real addresses (phone-only accounts
        // use a synthetic @phone.navgrow.local address that can't receive mail); a
        // short welcome SMS goes out when a phone number is on file. Both are
        // best-effort and never block a successful registration.
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            emailService.sendWelcomeEmail(req.getEmail(), req.getFullName());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            try {
                smsService.send(req.getPhone(), com.navgrow.enums.SmsEvent.WELCOME,
                    new java.util.LinkedHashMap<>(),
                    "Welcome to Navgrow Engineering! Your account is ready. Shop industrial supplies at navgrow.org");
            } catch (Exception e) {
                log.warn("Welcome SMS failed for {}: {}", req.getPhone(), e.getMessage());
            }
        }
        return ResponseEntity.status(201)
            .body(Map.of("message", "Registration successful. Please log in."));
    }

    // ── Refresh token ────────────────────────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestParam String refreshToken) {
        try {
            String email = jwtUtil.extractEmail(refreshToken);
            // Only refresh tokens may mint access tokens (legacy untyped tokens are
            // accepted until they expire), and never for a deactivated account.
            if (com.navgrow.security.JwtUtil.TYPE_ACCESS.equals(jwtUtil.extractType(refreshToken))) {
                throw new BadRequestException("Invalid or expired refresh token.");
            }
            UserDetails ud = uds.loadUserByUsername(email);
            if (!ud.isEnabled()) throw new BadRequestException("This account has been deactivated.");
            String newToken = jwtUtil.generateToken(ud);
            return ResponseEntity.ok(Map.of("accessToken", newToken, "tokenType", "Bearer"));
        } catch (BadRequestException e) {
            throw e;
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
