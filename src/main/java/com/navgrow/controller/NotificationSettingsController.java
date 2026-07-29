/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 */
package com.navgrow.controller;

import com.navgrow.entity.NotificationSettings;
import com.navgrow.repository.NotificationSettingsRepository;
import com.navgrow.service.EmailService;
import com.navgrow.service.SmsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin-only management of notification settings, plus a test-send so an admin
 * can prove email/SMS actually works from the panel.
 *
 * Credential fields are write-only in spirit: the GET masks secrets (auth keys /
 * tokens) so they're never echoed back to the browser, while still letting the
 * admin see which inboxes are configured.
 */
@RestController
@RequestMapping("/admin/notification-settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class NotificationSettingsController {

    private final NotificationSettingsRepository repo;
    private final EmailService emailService;
    private final SmsService smsService;

    private NotificationSettings loadOrNew() {
        return repo.findById(1).orElseGet(() -> NotificationSettings.builder().id(1).build());
    }

    private static String mask(String secret) {
        if (secret == null || secret.isBlank()) return "";
        return secret.length() <= 4 ? "••••" : "••••" + secret.substring(secret.length() - 4);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        NotificationSettings s = loadOrNew();
        // Secrets are masked; the UI shows whether they're set without exposing them.
        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("careersEmail",  nz(s.getCareersEmail())),
            Map.entry("ordersEmail",   nz(s.getOrdersEmail())),
            Map.entry("quotesEmail",   nz(s.getQuotesEmail())),
            Map.entry("contactEmail",  nz(s.getContactEmail())),
            Map.entry("supportEmail",  nz(s.getSupportEmail())),
            Map.entry("fromEmail",     nz(s.getFromEmail())),
            Map.entry("emailEnabled",  s.isEmailEnabled()),
            Map.entry("smsEnabled",    s.isSmsEnabled()),
            Map.entry("smsProvider",   nz(s.getSmsProvider())),
            Map.entry("smsSenderId",   nz(s.getSmsSenderId())),
            Map.entry("msg91AuthKeyMask",     mask(s.getMsg91AuthKey())),
            Map.entry("msg91TemplateId",      nz(s.getMsg91TemplateId())),
            Map.entry("twilioAccountSidMask", mask(s.getTwilioAccountSid())),
            Map.entry("twilioAuthTokenMask",  mask(s.getTwilioAuthToken())),
            Map.entry("twilioFromNumber",     nz(s.getTwilioFromNumber()))
        ));
    }

    private static String nz(String v) { return v == null ? "" : v; }

    @Data
    public static class SettingsReq {
        private String careersEmail, ordersEmail, quotesEmail, contactEmail, supportEmail, fromEmail;
        private Boolean emailEnabled, smsEnabled;
        private String smsProvider, smsSenderId;
        // Secrets: only applied when a non-blank value is sent, so leaving them
        // blank in the form never wipes an already-saved credential.
        private String msg91AuthKey, msg91TemplateId;
        private String twilioAccountSid, twilioAuthToken, twilioFromNumber;
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> save(@RequestBody SettingsReq req) {
        NotificationSettings s = loadOrNew();
        s.setCareersEmail(req.getCareersEmail());
        s.setOrdersEmail(req.getOrdersEmail());
        s.setQuotesEmail(req.getQuotesEmail());
        s.setContactEmail(req.getContactEmail());
        s.setSupportEmail(req.getSupportEmail());
        s.setFromEmail(req.getFromEmail());
        if (req.getEmailEnabled() != null) s.setEmailEnabled(req.getEmailEnabled());
        if (req.getSmsEnabled() != null)   s.setSmsEnabled(req.getSmsEnabled());
        s.setSmsProvider(req.getSmsProvider());
        s.setSmsSenderId(req.getSmsSenderId());
        s.setMsg91TemplateId(req.getMsg91TemplateId());
        s.setTwilioFromNumber(req.getTwilioFromNumber());
        // Only overwrite secrets when a fresh value is provided.
        if (req.getMsg91AuthKey() != null && !req.getMsg91AuthKey().isBlank())
            s.setMsg91AuthKey(req.getMsg91AuthKey().trim());
        if (req.getTwilioAccountSid() != null && !req.getTwilioAccountSid().isBlank())
            s.setTwilioAccountSid(req.getTwilioAccountSid().trim());
        if (req.getTwilioAuthToken() != null && !req.getTwilioAuthToken().isBlank())
            s.setTwilioAuthToken(req.getTwilioAuthToken().trim());
        repo.save(s);
        log.info("Notification settings updated by admin");
        return ResponseEntity.ok(Map.of("message", "Notification settings saved."));
    }

    /** Send a real test email to prove SMTP works. Reports the actual error on failure. */
    @PostMapping("/test-email")
    public ResponseEntity<Map<String, String>> testEmail(@RequestParam String to) {
        try {
            emailService.sendTestEmail(to);
            return ResponseEntity.ok(Map.of("message", "Test email sent to " + to + ". Check the inbox (and spam)."));
        } catch (Exception e) {
            String raw = e.getMessage() == null ? "unknown SMTP error" : e.getMessage();
            log.warn("Test email to {} failed: {}", to, raw);
            // Turn the raw SMTP error into something the admin can act on.
            String hint = "";
            String low = raw.toLowerCase();
            if (low.contains("authentication") || low.contains("535") || low.contains("credentials")) {
                hint = " — the mail server rejected the username/password. Check MAIL_USERNAME and "
                     + "MAIL_PASSWORD on the server. For Hostinger/Gmail you usually need the mailbox's "
                     + "own password (or an app-specific password), not your control-panel login.";
            } else if (low.contains("connect") || low.contains("timeout") || low.contains("could not connect")) {
                hint = " — could not reach the mail server. Check MAIL_HOST/MAIL_PORT and that outbound "
                     + "port 587 is open on the server.";
            } else if (low.contains("starttls") || low.contains("ssl") || low.contains("tls")) {
                hint = " — a TLS/SSL negotiation problem. Confirm the port (587 = STARTTLS, 465 = SSL) "
                     + "matches the mailbox settings.";
            }
            return ResponseEntity.status(502).body(Map.of("message", "Could not send: " + raw + hint));
        }
    }

    /** Send a real test SMS to prove the SMS provider works. */
    @PostMapping("/test-sms")
    public ResponseEntity<Map<String, String>> testSms(@RequestParam String to) {
        boolean ok = smsService.send(to, "Navgrow test SMS — your SMS configuration is working.");
        return ok
            ? ResponseEntity.ok(Map.of("message", "Test SMS sent to " + to + "."))
            : ResponseEntity.status(502).body(Map.of("message",
                "SMS not sent. Check the provider is set to msg91/twilio and credentials are correct (see logs)."));
    }
}
