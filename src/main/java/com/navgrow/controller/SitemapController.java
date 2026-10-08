/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import com.navgrow.enums.NewsStatus;
import com.navgrow.repository.NewsArticleRepository;
import com.navgrow.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic XML sitemap.
 *
 * The frontend build also writes a static sitemap, but it can only know the
 * pages that existed at build time. Admins publish news posts and products into
 * the DATABASE afterwards — those pages were therefore invisible to search
 * engines. This controller emits a live sitemap that always includes the core
 * marketing routes PLUS every currently-published news post and active product,
 * so new content is discoverable as soon as it goes live.
 *
 * Served at /sitemap-content.xml (and /sitemap.xml via the API). Point Search
 * Console / robots.txt at https://navgrow.org/sitemap-content.xml, or have the
 * web server proxy /sitemap.xml to this endpoint for a single canonical sitemap.
 */
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final NewsArticleRepository newsRepo;
    private final ProductRepository productRepo;

    @Value("${app.site-url:https://navgrow.org}")
    private String siteUrl;

    // Core marketing routes with a sensible crawl priority.
    private static final Map<String, String> STATIC_ROUTES = new LinkedHashMap<>();
    static {
        STATIC_ROUTES.put("/", "1.0");
        STATIC_ROUTES.put("/about", "0.8");
        STATIC_ROUTES.put("/services", "0.9");
        STATIC_ROUTES.put("/projects", "0.8");
        STATIC_ROUTES.put("/shop", "0.9");
        STATIC_ROUTES.put("/news", "0.8");
        STATIC_ROUTES.put("/careers", "0.6");
        STATIC_ROUTES.put("/gallery", "0.6");
        STATIC_ROUTES.put("/contact", "0.7");
        STATIC_ROUTES.put("/quote-calculator", "0.7");
    }

    @GetMapping(value = {"/sitemap-content.xml", "/sitemap.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        String base = siteUrl.replaceAll("/+$", "");
        String today = LocalDate.now().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        STATIC_ROUTES.forEach((path, priority) ->
            url(sb, base + path, today, path.equals("/") ? "daily" : "weekly", priority));

        // Published news posts
        try {
            for (var n : newsRepo.findByStatus(NewsStatus.PUBLISHED)) {
                if (n.getSlug() == null || n.getSlug().isBlank()) continue;
                url(sb, base + "/news/" + n.getSlug(), fmt(pick(n.getUpdatedAt(), n.getPublishedAt())),
                    "monthly", "0.7");
            }
        } catch (Exception ignored) { /* never fail the sitemap on bad data */ }

        // Active products
        try {
            for (var p : productRepo.findByActiveTrue()) {
                if (p.getSlug() == null || p.getSlug().isBlank()) continue;
                url(sb, base + "/shop/" + p.getSlug(), fmt(p.getUpdatedAt()), "weekly", "0.7");
            }
        } catch (Exception ignored) { /* never fail the sitemap on bad data */ }

        sb.append("</urlset>\n");
        return sb.toString();
    }

    private static void url(StringBuilder sb, String loc, String lastmod, String freq, String priority) {
        sb.append("  <url>\n")
          .append("    <loc>").append(escape(loc)).append("</loc>\n")
          .append("    <lastmod>").append(lastmod).append("</lastmod>\n")
          .append("    <changefreq>").append(freq).append("</changefreq>\n")
          .append("    <priority>").append(priority).append("</priority>\n")
          .append("  </url>\n");
    }

    private static LocalDateTime pick(LocalDateTime a, LocalDateTime b) { return a != null ? a : b; }

    private static String fmt(LocalDateTime dt) {
        if (dt == null) return LocalDate.now().toString();
        return dt.atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
