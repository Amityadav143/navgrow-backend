/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and matches the "deliver only to these pincodes" list an admin can set
 * on a product (e.g. perishable, oversized or locally-installed items).
 *
 * The list is free text, separated by commas, semicolons, spaces or new lines.
 * Each entry is one of:
 * <pre>
 *   734001          an exact 6-digit pincode
 *   734* or 734     a prefix (1–5 digits, '*' optional) — every pincode starting with it
 *   734001-734015   an inclusive range of pincodes
 * </pre>
 *
 * The same grammar is implemented in the frontend (src/lib/pincodeRules.js) so
 * the admin form can validate and preview instantly; this class is the
 * authority — it is what checkout and order creation enforce.
 */
public final class PincodeRules {

    private PincodeRules() {}

    /** Hard cap so a pasted spreadsheet can't create an unbounded rule list. */
    public static final int MAX_ENTRIES = 2000;

    private static final Pattern SPLIT   = Pattern.compile("[,;\\s]+");
    private static final Pattern EXACT   = Pattern.compile("^[1-9]\\d{5}$");
    private static final Pattern EXACT_STAR = Pattern.compile("^[1-9]\\d{5}\\*$");
    private static final Pattern PREFIX  = Pattern.compile("^[1-9]\\d{0,4}\\*?$");
    private static final Pattern RANGE   = Pattern.compile("^([1-9]\\d{5})-([1-9]\\d{5})$");
    private static final Pattern PINCODE = Pattern.compile("^[1-9]\\d{5}$");

    /** Raw, non-empty entries in the order they were written. */
    public static List<String> entries(String rules) {
        List<String> out = new ArrayList<>();
        if (rules == null || rules.isBlank()) return out;
        for (String raw : SPLIT.split(rules.trim())) {
            String t = raw.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Canonical form of one entry, or {@code null} if it isn't valid. */
    public static String canonical(String entry) {
        if (entry == null) return null;
        String t = entry.trim().replace('–', '-').replace('—', '-').replace(" ", "");
        if (EXACT.matcher(t).matches()) return t;
        if (EXACT_STAR.matcher(t).matches()) return t.substring(0, 6);   // "734001*" == "734001"
        if (PREFIX.matcher(t).matches()) return t.endsWith("*") ? t : t + "*";
        var m = RANGE.matcher(t);
        if (m.matches()) {
            long a = Long.parseLong(m.group(1));
            long b = Long.parseLong(m.group(2));
            if (a > b) { long tmp = a; a = b; b = tmp; }
            return a == b ? String.valueOf(a) : a + "-" + b;
        }
        return null;
    }

    /** Entries that are not valid (empty when the whole list is fine). */
    public static List<String> invalidEntries(String rules) {
        List<String> bad = new ArrayList<>();
        for (String e : entries(rules)) if (canonical(e) == null) bad.add(e);
        return bad;
    }

    /**
     * Validates and normalises a rule list for storage: canonical entries,
     * de-duplicated, joined by ", ". Returns {@code null} for an empty list.
     *
     * @throws IllegalArgumentException naming the invalid entries (shown to the admin)
     */
    public static String normalize(String rules) {
        List<String> all = entries(rules);
        if (all.isEmpty()) return null;
        if (all.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many pincode entries (" + all.size()
                + "). Use prefixes such as 734* or ranges such as 734001-734015 to shorten the list.");
        }
        List<String> bad = new ArrayList<>();
        Set<String> clean = new LinkedHashSet<>();
        for (String e : all) {
            String c = canonical(e);
            if (c == null) bad.add(e); else clean.add(c);
        }
        if (!bad.isEmpty()) {
            String shown = String.join(", ", bad.subList(0, Math.min(bad.size(), 8)));
            throw new IllegalArgumentException("Invalid pincode entr" + (bad.size() == 1 ? "y" : "ies") + ": " + shown
                + (bad.size() > 8 ? " …" : "")
                + ". Use a 6-digit pincode (734001), a prefix (734*) or a range (734001-734015).");
        }
        return String.join(", ", clean);
    }

    /** True when {@code pincode} is covered by at least one entry of {@code rules}. */
    public static boolean matches(String rules, String pincode) {
        if (pincode == null) return false;
        String pin = pincode.trim();
        if (!PINCODE.matcher(pin).matches()) return false;
        long pinNum = Long.parseLong(pin);
        for (String e : entries(rules)) {
            String c = canonical(e);
            if (c == null) continue;                       // ignore junk, never crash
            if (c.endsWith("*")) {
                if (pin.startsWith(c.substring(0, c.length() - 1))) return true;
            } else if (c.indexOf('-') > 0) {
                String[] p = c.split("-");
                if (pinNum >= Long.parseLong(p[0]) && pinNum <= Long.parseLong(p[1])) return true;
            } else if (c.equals(pin)) {
                return true;
            }
        }
        return false;
    }

    /** A short human summary for messages, e.g. "734001, 7340*, 734101-734105 (+12 more)". */
    public static String summary(String rules, int maxShown) {
        List<String> all = new ArrayList<>();
        for (String e : entries(rules)) { String c = canonical(e); if (c != null) all.add(c); }
        if (all.isEmpty()) return "";
        int n = Math.max(1, maxShown);
        String head = String.join(", ", all.subList(0, Math.min(n, all.size())));
        return all.size() > n ? head + " (+" + (all.size() - n) + " more)" : head;
    }
}
