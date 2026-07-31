/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 */
package com.navgrow.service;

/**
 * The transactional SMS events the app sends. Each maps to a DLT-approved MSG91
 * Flow template (id stored in NotificationSettings) plus the ordered variable
 * names the template expects.
 *
 * The variable names here MUST match the variable names used when the template
 * was created in MSG91/DLT (MSG91 Flow accepts named variables). The order is
 * documented so callers pass values in the right sequence.
 *
 * OTP is intentionally NOT here — it uses MSG91's dedicated OTP endpoint.
 */
public enum SmsEvent {
    WELCOME,            // C2  — no variables
    ORDER_COD,          // C3  — order_number, amount
    ORDER_ONLINE,       // C4  — order_number, amount
    ORDER_SHIPPED,      // C5  — order_number, tracking
    ORDER_DELIVERED,    // C6  — order_number
    ORDER_CANCELLED,    // C7  — order_number
    ORDER_PROCESSING,   // C8  — order_number
    ORDER_REFUNDED,     // C9  — order_number
    PASSWORD_CHANGED,   // C10 — no variables
    RFQ_RECEIVED,       // C11 — rfq_number
    RFQ_READY,          // C12 — rfq_number, amount
}
