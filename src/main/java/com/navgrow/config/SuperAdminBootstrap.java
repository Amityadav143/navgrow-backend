/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.config;

import com.navgrow.enums.UserRole;
import com.navgrow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Guarantees the platform always has at least one SUPER_ADMIN.
 *
 * Why: only a Super Admin can grant custom access or create Admins, and an Admin
 * cannot promote anyone to Super Admin (privilege-escalation guard). The V25
 * migration promotes the seeded admin@navgrow.org — but if that account doesn't
 * exist in a given database, nobody would be Super Admin and nobody could fix it
 * from the UI. On every startup this runner:
 *
 *   1. Promotes any EXISTING accounts listed in `app.super-admin-emails`
 *      (env SUPER_ADMIN_EMAILS, comma-separated). Register the account first.
 *   2. If there is still no Super Admin at all, promotes the OLDEST active ADMIN
 *      (an already-trusted, non-self-registrable account) and logs it.
 *
 * It never demotes anyone and never fails application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final UserRepository users;

    @Value("${app.super-admin-emails:}")
    private String superAdminEmails;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 1) Explicitly configured super admins.
            if (superAdminEmails != null && !superAdminEmails.isBlank()) {
                for (String raw : superAdminEmails.split(",")) {
                    String email = raw.trim();
                    if (email.isEmpty()) continue;
                    users.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(email).ifPresentOrElse(u -> {
                        if (u.getRole() != UserRole.SUPER_ADMIN) {
                            u.setRole(UserRole.SUPER_ADMIN);
                            users.save(u);
                            log.warn("[SuperAdminBootstrap] Promoted {} to SUPER_ADMIN (app.super-admin-emails).", email);
                        }
                    }, () -> log.warn("[SuperAdminBootstrap] {} is listed in app.super-admin-emails but no such account exists yet.", email));
                }
            }

            // 2) Safety net: never leave the platform without a Super Admin.
            if (users.countByRole(UserRole.SUPER_ADMIN) == 0) {
                users.findFirstByRoleAndActiveTrueOrderByCreatedAtAsc(UserRole.ADMIN).ifPresentOrElse(u -> {
                    u.setRole(UserRole.SUPER_ADMIN);
                    users.save(u);
                    log.warn("[SuperAdminBootstrap] No SUPER_ADMIN existed — promoted the oldest active ADMIN ({}) to SUPER_ADMIN.",
                        u.getEmail());
                }, () -> log.warn("[SuperAdminBootstrap] No SUPER_ADMIN and no active ADMIN found. " +
                                  "Set SUPER_ADMIN_EMAILS to an existing account's email and restart."));
            }
        } catch (Exception e) {
            // e.g. migrations not yet applied — never block startup over this.
            log.error("[SuperAdminBootstrap] Skipped: {}", e.getMessage());
        }
    }
}
