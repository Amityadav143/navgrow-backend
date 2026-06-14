/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.enums;

/** Lifecycle of a product Request-For-Quote (B2B procurement flow). */
public enum RfqStatus {
    SUBMITTED,   // buyer submitted the RFQ
    REVIEWING,   // admin is preparing the quote
    QUOTED,      // admin sent a priced quote back
    ACCEPTED,    // buyer accepted — ready to convert to order
    REJECTED,    // buyer declined the quote
    EXPIRED,     // quote validity passed
    CONVERTED,   // converted into a confirmed order
    CANCELLED    // cancelled by either party
}
