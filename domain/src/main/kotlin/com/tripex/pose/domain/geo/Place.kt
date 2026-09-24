package com.tripex.pose.domain.geo

/**
 * What kind of thing a geocoder returned. Drives the fallback reveal radius when the provider
 * gives no bounding box (see [RevealRadiusPolicy]).
 */
enum class PlaceKind {
    Country,
    Region,
    City,
    Town,
    Municipality,
    Village,
    Address,
    Poi,
    Unknown,
}

/**
 * A geocoded place from MapTiler (or another provider).
 */
data class Place(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val boundingBox: GeoBounds? = null,
    val kind: PlaceKind = PlaceKind.Unknown,
    /** Provider id, stable enough to key a cache and to deduplicate a result list. */
    val id: String = "",
    /**
     * Parent areas, coarsest last — e.g. `["mazowieckie", "Polska"]`.
     *
     * This is what tells four Warsaws apart in the suggestion list, and it arrives inside the
     * same response, so distinguishing homonyms never costs an extra request.
     */
    val context: List<String> = emptyList(),
    /**
     * ISO 3166-1 alpha-2, uppercase, when the provider knows it.
     *
     * The reliable way to find a searched **country** in the boundary bundle: the provider's
     * centre point can lie at sea (the Philippines, Indonesia, Denmark), where a point-in-polygon
     * lookup finds nothing and the country used to be treated as a mere point.
     */
    val countryCode: String? = null,
) {
    /** `Warszawa · mazowieckie, Polska` — enough to pick the right one at a glance. */
    val qualifiedName: String
        get() = if (context.isEmpty()) displayName else "$displayName · ${context.joinToString(", ")}"
}
