/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.service;

import com.navgrow.entity.AuditLog;
import com.navgrow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Semantic business-event auditing.
 *
 * The {@code AuditLogFilter} already records raw mutating HTTP requests; this
 * service adds named, searchable events ("NEWS_PUBLISH", "PRODUCT_DELETE", …)
 * so admins can trace exactly what happened, by whom, to which entity — both
 * in the Audit Log screen and in the application log file.
 *
 * Auditing is best-effort and must never break the calling request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository repo;

    /**
     * Record a business event.
     *
     * @param action     short action code, e.g. {@code NEWS_PUBLISH}
     * @param entityType entity name, e.g. {@code NewsArticle}
     * @param entityId   entity id (may be null)
     * @param detail     human-readable detail, e.g. the article title (may be null)
     */
    public void log(String action, String entityType, String entityId, String detail) {
        String user = currentUser();
        // Always emit to the application log — this is what ops tails on the server.
        log.info("EVENT {} {}/{} by={} {}", action, entityType, entityId, user,
                 detail != null ? "— " + detail : "");
        persist(action, entityType, entityId, detail, user);
    }

    private void persist(String action, String entityType, String entityId, String detail, String user) {
        try {
            String endpoint = action + " " + entityType + (entityId != null ? "/" + entityId : "")
                            + (detail != null ? " — " + detail : "");
            repo.save(AuditLog.builder()
                .method("EVENT")
                .endpoint(endpoint.length() > 300 ? endpoint.substring(0, 300) : endpoint)
                .userEmail(user)
                .build());
        } catch (Exception e) {
            log.debug("Audit persist skipped: {}", e.getMessage());
        }
    }

    private String currentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
                return auth.getName();
        } catch (Exception ignored) { }
        return "system";
    }
}
