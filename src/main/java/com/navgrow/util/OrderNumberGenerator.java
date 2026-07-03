/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.util;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OrderNumberGenerator {
    // Rolling in-process counter for readability within a run...
    private final AtomicInteger counter = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Format: NGO-{yyyyMMddHHmmss}-{seq}{rand}
     * The timestamp (to the second) plus an in-process sequence plus a random
     * tail make collisions effectively impossible — even across application
     * restarts (which reset the counter) or multiple running instances. This
     * matters because order_number carries a UNIQUE constraint, so a collision
     * would otherwise fail the order with a constraint violation.
     */
    public String generate() {
        int seq  = counter.getAndIncrement() % 1000;
        int rand = ThreadLocalRandom.current().nextInt(100, 1000);
        return "NGO-" + LocalDateTime.now().format(FMT) + "-"
             + String.format("%03d%03d", seq, rand);
    }
}