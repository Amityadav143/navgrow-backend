/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Verifies the SMTP connection once, at startup, and logs the result clearly.
 *
 * Transactional email is sent asynchronously and failures are caught and logged
 * per-message, which makes a bad credential or blocked port easy to miss. This
 * check surfaces the problem immediately in the application log instead of
 * leaving "no emails are arriving" as the only symptom.
 *
 * It never prevents start-up — a mail outage must not take the API down.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailHealthCheck {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:unset}")
    private String host;

    @Value("${spring.mail.port:0}")
    private int port;

    @Value("${spring.mail.username:unset}")
    private String username;

    @Value("${app.mail-healthcheck-enabled:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        if (!enabled) {
            log.info("MAIL: startup health check disabled.");
            return;
        }
        if (!(mailSender instanceof JavaMailSenderImpl impl)) {
            log.info("MAIL: sender is not JavaMailSenderImpl; skipping connection test.");
            return;
        }
        try {
            impl.testConnection();
            log.info("MAIL: SMTP connection OK — {}:{} as {}. Transactional email should send.",
                     host, port, username);
        } catch (Exception e) {
            log.error("""
                MAIL: SMTP CONNECTION FAILED — transactional email will NOT be delivered.
                  endpoint : {}:{}
                  username : {}
                  reason   : {}
                Check MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD, confirm the
                mailbox password is current, and confirm the host allows outbound SMTP on
                this port (587 = STARTTLS, 465 = SSL).""",
                host, port, username, e.getMessage());
        }
    }
}
