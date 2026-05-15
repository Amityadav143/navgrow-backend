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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

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

    @Data
    public static class LoginRequest {
        @Email @NotBlank String email;
        @NotBlank String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank String fullName;
        @Email @NotBlank String email;
        @NotBlank @Size(min=8) String password;
        String phone;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        UserDetails ud = uds.loadUserByUsername(req.getEmail());
        String token = jwtUtil.generateToken(ud);
        String refresh = jwtUtil.generateRefreshToken(ud);

        userRepo.findByEmail(req.getEmail()).ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            userRepo.save(u);
        });

        return ResponseEntity.ok(Map.of(
            "accessToken", token,
            "refreshToken", refresh,
            "tokenType", "Bearer",
            "email", ud.getUsername(),
            "roles", ud.getAuthorities()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new BadRequestException("Email already registered");
        }
        User user = User.builder()
            .fullName(req.getFullName())
            .email(req.getEmail())
            .passwordHash(encoder.encode(req.getPassword()))
            .phone(req.getPhone())
            .role(UserRole.USER)
            .build();
        userRepo.save(user);
        return ResponseEntity.status(201).body(Map.of("message", "Registration successful. Please log in."));
    }

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
}
