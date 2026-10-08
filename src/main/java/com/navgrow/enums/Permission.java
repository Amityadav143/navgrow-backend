/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fine-grained admin capabilities a SUPER_ADMIN can grant to any user, giving
 * custom access to specific areas of the admin panel regardless of base role.
 *
 * Each value maps 1:1 to an admin section. The authority string ("PERM_<NAME>")
 * is what Spring Security checks; the frontend uses the enum name to decide which
 * sidebar items to show. SUPER_ADMIN implicitly has ALL permissions.
 */
public enum Permission {
    // Commerce
    ORDERS,
    PRODUCTS,
    QUOTES,
    CATALOGUE_LEADS,
    TAX_RULES,
    DELIVERY_ZONES,
    RFQS,
    COUPONS,
    // CRM
    MESSAGES,
    USERS,            // view/manage users (NOT granting permissions — that's SUPER_ADMIN only)
    // Content
    NEWS,
    PROJECTS,
    GALLERY,
    JOBS,
    TENDERS,
    CATALOG,          // categories & services taxonomy
    // System
    SETTINGS,
    NOTIFICATIONS,
    AUDIT;

    /** Authority string used in Spring Security (hasAuthority("PERM_ORDERS")). */
    public String authority() {
        return "PERM_" + name();
    }

    /** All permissions — what SUPER_ADMIN and (by default) ADMIN receive. */
    public static Set<Permission> all() {
        return new LinkedHashSet<>(Arrays.asList(values()));
    }
}
