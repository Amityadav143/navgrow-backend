/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.repository;

import com.navgrow.entity.CatalogueLead;
import com.navgrow.enums.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface CatalogueLeadRepository extends JpaRepository<CatalogueLead, UUID> {
    Page<CatalogueLead> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<CatalogueLead> findByStatusOrderByCreatedAtDesc(LeadStatus status, Pageable pageable);
    long countByStatus(LeadStatus status);
    long countByCreatedAtAfter(LocalDateTime since);
}
