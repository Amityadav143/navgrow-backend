/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.enums;

public enum UserRole {
    /** Highest tier: full access to everything, including granting other users
     *  custom access (permissions). There should be very few of these. */
    SUPER_ADMIN,
    ADMIN,
    MANAGER,
    EDITOR,
    USER
}
