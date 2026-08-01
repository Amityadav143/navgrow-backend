/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Loads brand assets (the logo) once at startup and exposes them as a base64
 * data URI so they can be embedded directly in self-contained HTML — invoices
 * and emails — that renders identically in any client without an external fetch.
 */
@Slf4j
@Component
public class BrandAssets {

    private String logoDataUri = "";

    @PostConstruct
    void load() {
        try {
            var res = new ClassPathResource("static/brand/logo_email.png");
            try (var in = res.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                logoDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                log.info("Brand logo loaded for email/invoice embedding ({} bytes)", bytes.length);
            }
        } catch (Exception e) {
            log.warn("Brand logo not found on classpath — emails/invoices will fall back to text wordmark. {}", e.getMessage());
        }
    }

    /** Base64 data URI for the logo, or empty string if unavailable. */
    public String logoDataUri() { return logoDataUri; }

    public boolean hasLogo() { return logoDataUri != null && !logoDataUri.isEmpty(); }
}
