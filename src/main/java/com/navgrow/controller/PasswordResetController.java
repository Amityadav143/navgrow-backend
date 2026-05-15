package com.navgrow.controller;
import com.navgrow.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/auth") @RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetService passwordResetService;

    @Data public static class ResetRequest {
        @NotBlank String token;
        @NotBlank @Size(min = 8) String newPassword;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgot(@RequestParam @Email @NotBlank String email) {
        try {
            passwordResetService.initiateReset(email);
        } catch (Exception e) {
            // Don't reveal whether email exists — return same response
        }
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> reset(@Valid @RequestBody ResetRequest req) {
        passwordResetService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successful. Please log in."));
    }
}
