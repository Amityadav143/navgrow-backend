/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.controller;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.navgrow.config.CacheConfig;

import com.navgrow.entity.SiteSetting;
import com.navgrow.repository.SiteSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SiteSettingController — global site configuration.
 *  GET  /site-settings        → public, returns the saved JSON (or empty)
 *  PUT  /site-settings        → ADMIN, stores the full settings JSON
 */
@RestController
@RequestMapping("/site-settings")
@RequiredArgsConstructor
public class SiteSettingController {

    private static final String GLOBAL_KEY = "global";
    private final SiteSettingRepository repo;

    @GetMapping
    @Cacheable(CacheConfig.SITE_SETTINGS)
    public ResponseEntity<Map<String, String>> get() {
        String json = repo.findById(GLOBAL_KEY)
            .map(SiteSetting::getValue)
            .orElse("{}");
        return ResponseEntity.ok(Map.of("settings", json));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheConfig.SITE_SETTINGS, allEntries = true)
    public ResponseEntity<Map<String, String>> save(@RequestBody Map<String, Object> body) {
        // body is expected to contain {"settings": "<json string>"} or the raw object
        Object settings = body.getOrDefault("settings", body);
        String json = settings instanceof String ? (String) settings : toJson(settings);
        SiteSetting s = repo.findById(GLOBAL_KEY).orElse(
            SiteSetting.builder().key(GLOBAL_KEY).build());
        s.setValue(json);
        repo.save(s);
        return ResponseEntity.ok(Map.of("message", "Settings saved.", "settings", json));
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
