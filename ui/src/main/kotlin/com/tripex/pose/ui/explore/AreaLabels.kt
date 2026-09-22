package com.tripex.pose.ui.explore

import java.util.Locale

/**
 * Human names for administrative areas.
 *
 * Country names come from the platform's own ISO-3166 tables, which are already translated and
 * cost nothing to ship. The boundary bundle's `name_pl` / `name` is the fallback for codes the
 * platform does not know — and the only source for regions, which ICU does not name.
 *
 * The locale defaults to the JVM's, which `AppLanguageApplier` in `:app` keeps equal to the
 * chosen language. That indirection is deliberate: these labels are built in view models, which
 * have no `Context` and therefore cannot read the composition's locale. Taking the locale as a
 * parameter keeps it substitutable in tests instead of implicit (V3.5.6).
 */
internal object AreaLabels {

    private const val ISO2_LENGTH = 2

    fun country(
        iso2: String,
        fromBundle: String? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        val code = iso2.trim().uppercase()
        // Natural Earth uses codes like `-99`, which `Locale.Builder` rejects outright.
        val fromPlatform = if (code.length == ISO2_LENGTH && code.all { it in 'A'..'Z' }) {
            runCatching {
                Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
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
