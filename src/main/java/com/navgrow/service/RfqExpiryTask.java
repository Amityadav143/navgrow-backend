/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.service;

import com.navgrow.entity.Rfq;
import com.navgrow.enums.RfqStatus;
import com.navgrow.repository.RfqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically flips quoted RFQs whose validity has lapsed to EXPIRED, so the
 * buyer's "Saved Quotes" view and the admin list stay accurate even when no one
 * actively opens the quote. Runs hourly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RfqExpiryTask {

    private final RfqRepository rfqRepo;

    // Every hour, at the top of the hour.
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireStaleQuotes() {
        LocalDateTime now = LocalDateTime.now();
        List<Rfq> stale = rfqRepo.findByStatusAndQuoteValidUntilBefore(RfqStatus.QUOTED, now);
        if (stale.isEmpty()) return;
        for (Rfq rfq : stale) {
            rfq.setStatus(RfqStatus.EXPIRED);
        }
        rfqRepo.saveAll(stale);
        log.info("Auto-expired {} quoted RFQ(s) past their validity date", stale.size());
    }
}
