/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.repository;
import com.navgrow.entity.Tender;
import com.navgrow.enums.TenderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenderRepository extends JpaRepository<Tender, UUID> {
    List<Tender> findByStatusOrderByDeadlineAsc(TenderStatus status);
    List<Tender> findByFeaturedTrueAndStatusOrderByDeadlineAsc(TenderStatus status);
}