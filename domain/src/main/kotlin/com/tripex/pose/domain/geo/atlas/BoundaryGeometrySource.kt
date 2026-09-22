package com.tripex.pose.domain.geo.atlas

import com.tripex.pose.domain.settings.AppLanguage

/**
 * Feature hit from the boundaries PMTiles bundle (for geocoder matching, M4.6).
 */
data class BoundaryFeature(
    val level: AdminLevel,
    val id: String,
    val name: String,
    /** Localized name from the bundle, when the source data carries one. */
    val namePl: String?,
    val countryIso2: String?,
    /** Continent id from the bundle (ADM0 only) — drives the palette a country is entered with. */
    val continentId: String? = null,
) {
    /**
     * Name to put in front of a player, in their language (V3.5.6).
     *
     * The bundle ships an English `name` and, where the source had one, a Polish `name_pl`.
     * Preferring `name_pl` unconditionally — which is what [displayName] did — is why an English
     * search for Germany answered "Niemcy".
     */
    fun nameIn(language: AppLanguage): String = when (language) {
        AppLanguage.Polish -> namePl?.takeIf { it.isNotBlank() } ?: name
        AppLanguage.English -> name.takeIf { it.isNotBlank() } ?: namePl.orEmpty()
    }

    /**
     * The local form, regardless of interface language.
     *
     * Only a fallback now, for area labels that ICU cannot name — a Polish voivodeship has no
     * English name worth showing, so the local one is the right answer in both languages. Use
     * [nameIn] for anything the player reads as prose.
     */
    val displayName: String get() = namePl?.takeIf { it.isNotBlank() } ?: name
}

/**
 * Reads country/region rings from the local PMTiles bundle for H3 math.
 * Not for rendering — MapLibre reads the same file via [BoundaryTilesProvider].
 */
interface BoundaryGeometrySource {
    suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>>

    /** Attributes of one feature by its id, e.g. to label a screen entered from a saved route. */
    suspend fun feature(level: AdminLevel, id: String): BoundaryFeature?

    suspend fun featureAt(
        level: AdminLevel,
        lat: Double,
        lng: Double,
    ): BoundaryFeature?
}
