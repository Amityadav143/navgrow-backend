/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.repository;

import com.navgrow.entity.Rfq;
import com.navgrow.enums.RfqStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RfqRepository extends JpaRepository<Rfq, UUID> {

    Optional<Rfq> findByRfqNumber(String rfqNumber);

    Page<Rfq> findByStatusOrderByCreatedAtDesc(RfqStatus status, Pageable pageable);

    Page<Rfq> findByBuyerEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    Page<Rfq> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByStatus(RfqStatus status);
}
