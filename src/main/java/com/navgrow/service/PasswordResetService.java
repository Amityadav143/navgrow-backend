package com.navgrow.service;
import com.navgrow.entity.PasswordResetToken;
import com.navgrow.entity.User;
import com.navgrow.exception.BadRequestException;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.PasswordResetTokenRepository;
import com.navgrow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor @Slf4j
public class PasswordResetService {
    private final UserRepository              userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final EmailService                emailService;
    private final PasswordEncoder             encoder;

    @Transactional
    public void initiateReset(String email) {
        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("No account found with email: " + email));
        tokenRepo.deleteByUserId(user.getId());
        String token = UUID.randomUUID().toString().replace("-","");
        PasswordResetToken prt = PasswordResetToken.builder()
            .user(user).token(token)
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();
        tokenRepo.save(prt);
        emailService.sendPasswordResetEmail(email, user.getFullName(), token);
        log.info("Password reset initiated for: {}", email);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = tokenRepo.findByTokenAndUsedFalse(token)
            .orElseThrow(() -> new BadRequestException("Invalid or expired reset token."));
        if (prt.isExpired()) {
            tokenRepo.delete(prt);
            throw new BadRequestException("Reset link has expired. Please request a new one.");
        }
        User user = prt.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        userRepo.save(user);
        prt.setUsed(true);
        tokenRepo.save(prt);
        log.info("Password reset successful for: {}", user.getEmail());
    }
}
