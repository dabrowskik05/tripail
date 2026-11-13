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
     * Name tag to read off the basemap's label layers (OpenMapTiles schema), with a fallback chain.
     *
     * Not every feature carries every translation, and a label that resolves to nothing renders
     * as an empty string — a nameless city is worse than one named in the wrong language.
     */
    val mapNameTags: List<String>
        get() = when (this) {
            Polish -> listOf("name:pl", "name_pl", "name:en", "name")
            English -> listOf("name:en", "name_en", "name")
        }

    /**
     * Language passed to the geocoder (Photon). Its public instance knows only `default` (each
     * place's local name), `en`, `de` and `fr` — Polish is not available, so a Polish UI gets
     * local names ("Roma", "Warszawa"), which beats English ones for a Polish speaker.
     */
    val geocoderLanguage: String
        get() = when (this) {
            Polish -> "default"
            English -> "en"
        }

    companion object {
        val DEFAULT: AppLanguage = Polish

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: DEFAULT
    }
}
