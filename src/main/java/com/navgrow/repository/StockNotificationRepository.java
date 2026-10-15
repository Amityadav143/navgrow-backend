/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.repository;

import com.navgrow.entity.StockNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockNotificationRepository extends JpaRepository<StockNotification, UUID> {

    Optional<StockNotification> findByEmailIgnoreCaseAndProductSlug(String email, String productSlug);

    List<StockNotification> findByProductSlugAndNotifiedFalse(String productSlug);

    List<StockNotification> findByNotifiedFalseOrderByCreatedAtDesc();

    long countByNotifiedFalse();

    long countByProductSlugAndNotifiedFalse(String productSlug);

    /** Pent-up demand grouped by product: [slug, name, count] for the pending list. */
    @Query("SELECT s.productSlug, MAX(s.productName), COUNT(s) " +
           "FROM StockNotification s WHERE s.notified = false " +
           "GROUP BY s.productSlug ORDER BY COUNT(s) DESC")
    List<Object[]> demandByProduct();
}
