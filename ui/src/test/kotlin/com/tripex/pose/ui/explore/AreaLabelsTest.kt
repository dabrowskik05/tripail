package com.tripex.pose.ui.explore

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AreaLabelsTest {

    @Test
    fun `known codes are named by the platform`() {
        // Locale-dependent wording, so assert only that a real name replaced the code.
        val name = AreaLabels.country("PL")
        assertEquals(false, name.equals("PL", ignoreCase = true))
    }

    /** The interface language decides, not the device's — see `AppLanguageApplier` (V3.5.6). */
    @Test
    fun `country names follow the locale they are asked for`() {
        assertEquals("Germany", AreaLabels.country("DE", locale = Locale.ENGLISH))
        assertEquals("Niemcy", AreaLabels.country("DE", locale = Locale.forLanguageTag("pl")))
    }

    @Test
    fun `unknown codes fall back to the bundle name, then to the code`() {
        assertEquals("Kosowo", AreaLabels.country("-99", fromBundle = "Kosowo"))
        assertEquals("-99", AreaLabels.country("-99", fromBundle = null))
        assertEquals("-99", AreaLabels.country("-99", fromBundle = "  "))
    }

    @Test
    fun `regions are named only by the bundle`() {
        assertEquals("Mazowieckie", AreaLabels.region("POL-14", fromBundle = "Mazowieckie"))
        assertEquals("POL-14", AreaLabels.region("POL-14", fromBundle = null))
    }
}
