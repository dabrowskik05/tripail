package com.tripex.pose.ui.explore

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryFlagTest {

    @Test
    fun `valid codes map to regional indicator pairs`() {
        assertEquals("🇵🇱", CountryFlag.of("PL"))
        assertEquals("🇩🇪", CountryFlag.of("de"))
        assertEquals("🇯🇵", CountryFlag.of(" jp "))
    }

    @Test
    fun `codes outside the standard fall back to a white flag`() {
        // Natural Earth uses -99 for disputed or unrecognised territories.
        assertEquals(CountryFlag.FALLBACK, CountryFlag.of("-99"))
        assertEquals(CountryFlag.FALLBACK, CountryFlag.of(""))
        assertEquals(CountryFlag.FALLBACK, CountryFlag.of("POL"))
        assertEquals(CountryFlag.FALLBACK, CountryFlag.of("P1"))
        assertEquals(CountryFlag.FALLBACK, CountryFlag.of(null))
    }
}
