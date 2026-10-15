/* © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved. */
package com.navgrow.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Mirrors src/__tests__/pincodeRules.test.js in the frontend — keep them in step. */
class PincodeRulesTest {

    private static final String RULES = "734001, 7340*, 735 ; 736101-736105\n700001";

    @Test
    void normalize_canonicalisesEveryEntryType() {
        assertEquals("734001, 7340*, 735*, 736101-736105, 700001", PincodeRules.normalize(RULES));
        assertEquals("734001", PincodeRules.normalize("734001,734001, 734001"));
        assertNull(PincodeRules.normalize("   "));
    }

    @Test
    void normalize_rejectsInvalidEntriesWithAHelpfulMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PincodeRules.normalize("734001, abc"));
        assertTrue(e.getMessage().contains("abc"));
    }

    @Test
    void matches_exactPrefixAndRange() {
        assertTrue(PincodeRules.matches(RULES, "734001"));
        assertTrue(PincodeRules.matches(RULES, "734005"));
        assertFalse(PincodeRules.matches(RULES, "734105"));
        assertTrue(PincodeRules.matches(RULES, "735222"));
        assertTrue(PincodeRules.matches(RULES, "736103"));
        assertFalse(PincodeRules.matches(RULES, "736106"));
        assertTrue(PincodeRules.matches(RULES, "700001"));
    }

    @Test
    void matches_rejectsMalformedPincodesAndEmptyRules() {
        assertFalse(PincodeRules.matches(RULES, "73600"));
        assertFalse(PincodeRules.matches(RULES, "034001"));
        assertFalse(PincodeRules.matches(null, "734001"));
        assertFalse(PincodeRules.matches("", "734001"));
    }

    @Test
    void canonical_handlesReversedRangesDashesAndExactStar() {
        assertEquals("736101-736105", PincodeRules.canonical("736105-736101"));
        assertEquals("734001-734009", PincodeRules.canonical("734001–734009"));
        assertEquals("734001", PincodeRules.canonical("734001*"));
        assertEquals("734*", PincodeRules.canonical("734"));
        assertNull(PincodeRules.canonical("0734"));
        assertNull(PincodeRules.canonical("7340011"));
    }

    @Test
    void summary_truncatesLongLists() {
        assertEquals("734001, 7340* (+2 more)", PincodeRules.summary("734001,7340*,735*,736*", 2));
    }
}
