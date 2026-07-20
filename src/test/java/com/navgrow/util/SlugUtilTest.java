/* © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved. */
package com.navgrow.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlugUtilTest {

    private final SlugUtil slugUtil = new SlugUtil();

    @Test
    void toSlug_normalisesCaseSpacesAndSymbols() {
        assertEquals("hydraulic-hand-brake-m12", slugUtil.toSlug("Hydraulic Hand-Brake (M12)!"));
        assertEquals("railway-tools", slugUtil.toSlug("  Railway   Tools  "));
    }

    @Test
    void toSlug_stripsDiacritics() {
        assertEquals("resume-cafe", slugUtil.toSlug("Résumé Café"));
    }

    @Test
    void uniqueSlug_appendsCounterOnCollision() {
        java.util.Set<String> taken = new java.util.HashSet<>(java.util.Set.of("pump", "pump-1"));
        assertEquals("pump-2", slugUtil.uniqueSlug("Pump", taken::contains));
        assertEquals("valve", slugUtil.uniqueSlug("Valve", taken::contains));
    }
}
