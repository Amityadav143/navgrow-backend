/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
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
