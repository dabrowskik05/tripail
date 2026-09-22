package com.tripex.pose.domain.settings

/**
 * The two languages Tripail speaks (V3.5.1).
 *
 * One value drives three separate things, which is the point of having it in the domain: the
 * interface strings, the labels baked into the map's vector tiles, and the language the geocoder
 * answers in. Letting any of them follow the device locale independently is how you end up with
 * a Polish interface listing English city names.
 */
enum class AppLanguage(val tag: String) {
    Polish("pl"),
    English("en"),
    ;

    /**
     * Name tag to read off MapTiler's label layers, with a fallback chain.
     *
     * Not every feature carries every translation, and a label that resolves to nothing renders
     * as an empty string — a nameless city is worse than one named in the wrong language.
     */
    val mapNameTags: List<String>
        get() = when (this) {
            Polish -> listOf("name:pl", "name_pl", "name:en", "name")
            English -> listOf("name:en", "name_en", "name")
        }

    /** What to ask the geocoder for. Both languages are sent, preferred one first. */
    val geocodingLanguages: String
        get() = when (this) {
            // "norwegia" is indexed under the Polish name, "Norway" under the English one.
            // Asking for one language only is why a full Polish country name found nothing.
            Polish -> "pl,en"
            English -> "en,pl"
        }

    companion object {
        val DEFAULT: AppLanguage = Polish

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: DEFAULT
    }
}
