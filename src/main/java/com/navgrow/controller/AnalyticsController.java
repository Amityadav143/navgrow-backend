/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;
import com.navgrow.enums.*;
import com.navgrow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AnalyticsController {
    private final OrderRepository          orderRepo;
    private final ContactMessageRepository contactRepo;
    private final QuoteRequestRepository   quoteRepo;
    private final NewsletterRepository     newsletterRepo;
    private final ProductRepository        productRepo;
    private final JobApplicationRepository appRepo;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        LocalDateTime last30 = LocalDateTime.now().minusDays(30);
        BigDecimal revenue   = orderRepo.sumRevenueFrom(last30);

        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("totalOrders",          orderRepo.count()),
            Map.entry("pendingOrders",         orderRepo.countByStatus(OrderStatus.PENDING)),
            Map.entry("confirmedOrders",        orderRepo.countByStatus(OrderStatus.CONFIRMED)),
            Map.entry("revenueLastMonth",       revenue != null ? revenue : BigDecimal.ZERO),
            Map.entry("unreadMessages",         contactRepo.countUnread()),
            Map.entry("newQuotes",              quoteRepo.countByStatus(QuoteStatus.NEW)),
            Map.entry("newsletterSubscribers",  newsletterRepo.countByActiveTrue()),
            Map.entry("activeProducts",         productRepo.count()),
            Map.entry("newApplications",        appRepo.countByStatus(ApplicationStatus.NEW)),
            Map.entry("openJobs",               0L) // placeholder
        ));
    }

    @GetMapping("/recent-orders")
    public ResponseEntity<?> recentOrders() {
        return ResponseEntity.ok(orderRepo.findRecentOrders(LocalDateTime.now().minusDays(7)));
    }
}