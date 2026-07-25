/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mail diagnostics.
 *
 * Transactional email is sent asynchronously and each method catches its own
 * exception, so a bad password or a blocked port produces silence rather than an
 * error — "no emails are arriving" with nothing to act on. These endpoints run
 * the same send path synchronously and return the underlying SMTP failure, which
 * turns an invisible problem into a specific one.
 */
@RestController
@RequestMapping("/admin/mail")
@RequiredArgsConstructor
@Slf4j
public class MailDiagnosticsController {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:unset}")     private String host;
    @Value("${spring.mail.port:0}")         private int port;
    @Value("${spring.mail.username:unset}") private String username;
    @Value("${app.contact-email:info@navgrow.org}") private String contactEmail;

    /** Shows the effective configuration without ever exposing the password. */
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("host", host);
        out.put("port", port);
        out.put("username", username);
        out.put("officeInbox", contactEmail);
        out.put("passwordConfigured", passwordPresent());
        out.put("senderClass", mailSender.getClass().getSimpleName());
        return ResponseEntity.ok(out);
    }

    /** Opens an SMTP connection and reports exactly why it failed, if it did. */
    @GetMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> verify() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("host", host);
        out.put("port", port);
        out.put("username", username);
        if (!(mailSender instanceof JavaMailSenderImpl impl)) {
            out.put("ok", false);
            out.put("error", "Mail sender is not JavaMailSenderImpl — cannot test the connection.");
            return ResponseEntity.ok(out);
        }
        try {
            impl.testConnection();
            out.put("ok", true);
            out.put("message", "SMTP connection and authentication succeeded.");
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", e.getMessage());
            out.put("errorType", e.getClass().getSimpleName());
            out.put("hint", hintFor(e));
            log.error("Mail verify failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(out);
    }

    /** Sends a real message synchronously so the true error surfaces. */
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> test(@RequestParam(required = false) String to) {
        String recipient = (to == null || to.isBlank()) ? contactEmail : to.trim();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("to", recipient);
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(recipient);
            helper.setSubject("Navgrow mail test — " + LocalDateTime.now());
            helper.setText("""
                <div style="font-family:Arial,sans-serif">
                  <h3>Mail is working</h3>
                  <p>This test was sent from the Navgrow admin console. If you are reading
                     it, transactional email (orders, quotes, enquiries, catalogue leads)
                     can be delivered.</p>
                </div>""", true);
            mailSender.send(mime);
            out.put("ok", true);
            out.put("message", "Test email sent. Check the inbox (and the spam folder).");
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", e.getMessage());
            out.put("errorType", e.getClass().getSimpleName());
            out.put("hint", hintFor(e));
            log.error("Mail test send failed: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(out);
    }

    private boolean passwordPresent() {
        if (mailSender instanceof JavaMailSenderImpl impl) {
            String p = impl.getPassword();
            return p != null && !p.isBlank();
        }
        return false;
    }

    /** Turns the common SMTP failures into something actionable. */
    private String hintFor(Exception e) {
        String m = (e.getMessage() == null ? "" : e.getMessage()).toLowerCase();
        if (m.contains("authentication") || m.contains("535") || m.contains("username and password"))
            return "The mailbox rejected the credentials. Confirm MAIL_USERNAME and MAIL_PASSWORD "
                 + "are current — a rotated or expired mailbox password produces exactly this.";
        if (m.contains("timed out") || m.contains("timeout") || m.contains("connect"))
            return "Could not reach the SMTP server. The host may block outbound SMTP; check the "
                 + "port (587 = STARTTLS, 465 = SSL) and any firewall rules.";
        if (m.contains("starttls") || m.contains("ssl") || m.contains("handshake"))
            return "TLS negotiation failed. If the server expects implicit SSL, use port 465 and "
                 + "set spring.mail.properties.mail.smtp.ssl.enable=true instead of STARTTLS.";
        if (m.contains("relay") || m.contains("sender") || m.contains("from"))
            return "The server refused the sender address. The From address must match the "
                 + "authenticated mailbox exactly.";
        return "Check the full stack trace in the application log for the underlying cause.";
    }
}
