package com.tripex.pose.ui.explore

import java.util.Locale

/**
 * Human names for administrative areas.
 *
 * Country names come from the platform's own ISO-3166 tables, which are already translated and
 * cost nothing to ship. The boundary bundle's `name_pl` / `name` is the fallback for codes the
 * platform does not know — and the only source for regions, which ICU does not name.
 */
internal object AreaLabels {

    private const val ISO2_LENGTH = 2

    fun country(iso2: String, fromBundle: String? = null): String {
        val code = iso2.trim().uppercase()
        // Natural Earth uses codes like `-99`, which `Locale.Builder` rejects outright.
        val fromPlatform = if (code.length == ISO2_LENGTH && code.all { it in 'A'..'Z' }) {
            runCatching {
                Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
            }.getOrNull()
        } else {
            null
        }
        // ICU echoes the input back when it does not recognise the region.
        return when {
            !fromPlatform.isNullOrBlank() && !fromPlatform.equals(code, ignoreCase = true) -> fromPlatform
            !fromBundle.isNullOrBlank() -> fromBundle
            else -> code
        }
    }

    fun region(regionId: String, fromBundle: String?): String =
        fromBundle?.takeIf { it.isNotBlank() } ?: regionId
}
