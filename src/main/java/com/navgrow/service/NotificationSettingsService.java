/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 */
package com.navgrow.service;

import com.navgrow.entity.NotificationSettings;
import com.navgrow.repository.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "where does each kind of notification go" and
 * "is email/SMS on right now".
 *
 * Resolution order for every value: the admin-edited DB row wins if it has a
 * value; otherwise fall back to application.yml / environment. This lets the
 * office repoint the careers or orders inbox from the admin panel without a
 * redeploy, while a fresh install with no DB row still works from env config.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSettingsService {

    private final NotificationSettingsRepository repo;

    // Environment/yaml fallbacks.
    @Value("${app.contact-email:info@navgrow.org}") private String envContactEmail;
    @Value("${app.careers-email:careers@navgrow.org}") private String envCareersEmail;
    @Value("${spring.mail.username:info@navgrow.org}") private String envFromEmail;

    /** The persisted settings, or null if the admin has never saved any. */
    public NotificationSettings current() {
        return repo.findById(1).orElse(null);
    }

    private String pick(String dbValue, String fallback) {
        return (dbValue != null && !dbValue.isBlank()) ? dbValue.trim() : fallback;
    }

    // ── Recipients (internal inboxes) ─────────────────────────────────────────
    public String careersEmail() { var s = current(); return pick(s == null ? null : s.getCareersEmail(), envCareersEmail); }
    public String ordersEmail()  { var s = current(); return pick(s == null ? null : s.getOrdersEmail(),  envContactEmail); }
    public String quotesEmail()  { var s = current(); return pick(s == null ? null : s.getQuotesEmail(),  envContactEmail); }
    public String contactEmail() { var s = current(); return pick(s == null ? null : s.getContactEmail(), envContactEmail); }
    public String supportEmail() { var s = current(); return pick(s == null ? null : s.getSupportEmail(), envContactEmail); }
    public String fromEmail()    { var s = current(); return pick(s == null ? null : s.getFromEmail(),    envFromEmail); }

    // ── Master switches ───────────────────────────────────────────────────────
    public boolean emailEnabled() { var s = current(); return s == null || s.isEmailEnabled(); }
    public boolean smsEnabled()   { var s = current(); return s == null || s.isSmsEnabled(); }
}
