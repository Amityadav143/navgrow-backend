/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.repository;

import com.navgrow.entity.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, String> {

    /**
     * Fetch the series row with a row-level write lock so two concurrent orders
     * cannot read the same last_seq and produce a duplicate invoice number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSequence s where s.finYear = :finYear")
    Optional<InvoiceSequence> findForUpdate(@Param("finYear") String finYear);
}
