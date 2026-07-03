/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.util;

/**
 * Small helpers to keep pagination parameters within safe bounds, so a client
 * cannot request an unbounded page size (e.g. ?size=999999) and force the
 * database to materialise a huge result set.
 */
public final class PageUtil {
    private PageUtil() {}

    public static final int MAX_PAGE_SIZE = 100;

    /** Clamp the page index to be non-negative. */
    public static int safePage(int page) {
        return Math.max(0, page);
    }

    /** Clamp the page size into the range [1, MAX_PAGE_SIZE]. */
    public static int safeSize(int size) {
        if (size < 1) return 1;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
