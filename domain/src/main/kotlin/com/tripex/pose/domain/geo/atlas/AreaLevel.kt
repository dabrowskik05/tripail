package com.tripex.pose.domain.geo.atlas

/**
 * Maps a navigable area onto the admin level that carries its geometry in `boundaries.pmtiles`.
 * Continents come from the overview atlas and cities only from the geocoder, so neither has a
 * level here — that is a fact about the bundle, not an omission.
 */
fun AreaKey.boundaryLevel(): AdminLevel? = when (this) {
    is AreaKey.Country -> AdminLevel.Adm0
    is AreaKey.Region -> AdminLevel.Adm1
    is AreaKey.Continent, is AreaKey.City -> null
}

/** Feature id used inside the bundle for this area, or `null` when it has no boundary geometry. */
fun AreaKey.boundaryId(): String? = when (this) {
    is AreaKey.Country -> iso2
    is AreaKey.Region -> id
    is AreaKey.Continent, is AreaKey.City -> null
}
