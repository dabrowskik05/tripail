package com.tripex.pose.ui.explore

/**
 * Flag emoji for an ISO 3166-1 alpha-2 code, built from regional indicator symbols (M3.2).
 *
 * No image assets: `PL` becomes U+1F1F5 U+1F1F1, which every modern Android font renders as 🇵🇱.
 * Anything outside the standard — including the `-99` placeholders Natural Earth uses for
 * disputed territories — falls back to a white flag rather than an empty box.
 */
internal object CountryFlag {

    const val FALLBACK = "🏳️"

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
    private const val ISO2_LENGTH = 2

    fun of(iso2: String?): String {
        val code = iso2?.trim()?.uppercase() ?: return FALLBACK
        if (code.length != ISO2_LENGTH || code.any { it !in 'A'..'Z' }) return FALLBACK
        val builder = StringBuilder()
        for (char in code) {
            builder.appendCodePoint(REGIONAL_INDICATOR_A + (char - 'A'))
        }
        return builder.toString()
    }
}
