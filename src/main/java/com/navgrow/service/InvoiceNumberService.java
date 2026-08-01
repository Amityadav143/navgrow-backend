/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.service;

import com.navgrow.entity.InvoiceSequence;
import com.navgrow.entity.Order;
import com.navgrow.repository.InvoiceSequenceRepository;
import com.navgrow.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Assigns GST-compliant, sequential invoice numbers of the form
 * {@code NG/2025-26/000123}. Numbers are unique and gap-free per Indian
 * financial year (Apr–Mar), backed by a locked counter row so concurrent
 * orders can never collide.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private final InvoiceSequenceRepository seqRepo;
    private final OrderRepository orderRepo;

    /** Indian financial year label for a date, e.g. 2025-06-10 -> "2025-26". */
    public static String financialYear(LocalDate date) {
        int y = date.getYear();
        // FY starts 1 April.
        int startYear = date.getMonthValue() >= 4 ? y : y - 1;
        int endYY = (startYear + 1) % 100;
        return String.format("%d-%02d", startYear, endYY);
    }

    /**
     * Ensure the order has an invoice number and date, assigning one atomically
     * if absent. Idempotent: an order that already has a number is untouched, so
     * re-confirmation or retries never issue a second number for the same order.
     *
     * Runs in its own transaction so the locked counter row is released promptly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String assignIfAbsent(Order order) {
        if (order.getInvoiceNumber() != null && !order.getInvoiceNumber().isBlank()) {
            return order.getInvoiceNumber();
        }
        LocalDate today = LocalDate.now();
        String fy = financialYear(today);

        InvoiceSequence seq = seqRepo.findForUpdate(fy).orElseGet(() -> {
            InvoiceSequence s = InvoiceSequence.builder()
                    .finYear(fy).lastSeq(0L).updatedAt(LocalDateTime.now()).build();
            return seqRepo.saveAndFlush(s);
        });

        long next = seq.getLastSeq() + 1;
        seq.setLastSeq(next);
        seq.setUpdatedAt(LocalDateTime.now());
        seqRepo.saveAndFlush(seq);

        String number = String.format("NG/%s/%06d", fy, next);
        order.setInvoiceNumber(number);
        if (order.getInvoiceDate() == null) order.setInvoiceDate(LocalDateTime.now());
        orderRepo.save(order);
        log.info("Assigned invoice number {} to order {}", number, order.getOrderNumber());
        return number;
    }
}
