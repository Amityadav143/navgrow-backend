/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process Caffeine cache for hot public reads (product categories, site
 * settings, open tenders, gallery, catalog). Short TTL keeps content fresh;
 * admin mutations also evict eagerly via @CacheEvict.
 *
 * Deliberately NOT Redis: this is a single-server deployment, so an
 * in-process cache gives the same win with zero new infrastructure.
 */
@Configuration
public class CacheConfig {

    public static final String PRODUCT_CATEGORIES = "productCategories";
    public static final String SITE_SETTINGS      = "siteSettings";
    public static final String TENDERS_PUBLIC     = "tendersPublic";
    public static final String GALLERY_PUBLIC     = "galleryPublic";
    public static final String CATALOG_PUBLIC     = "catalogPublic";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            PRODUCT_CATEGORIES, SITE_SETTINGS, TENDERS_PUBLIC, GALLERY_PUBLIC, CATALOG_PUBLIC);
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(120))
            .maximumSize(500));
        return manager;
    }
}
