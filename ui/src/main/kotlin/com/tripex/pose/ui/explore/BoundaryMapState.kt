package com.tripex.pose.ui.explore

import androidx.compose.runtime.Immutable

/**
 * What the shared boundary surface is currently showing (M3.1).
 *
 * Switching [selectedId] is a filter change on layers that already exist — never a new source,
 * a rebuilt style or fresh geometry. That is the whole point of this type: it carries the little
 * that actually varies between the country and region levels.
 *
 * It deliberately holds **no map data**. The style URI, the boundary tiles and the parchment
 * geometry belong to the single map host above the navigation graph; a level that kept its own
 * copy meant the same 20 000-cell GeoJSON being rebuilt once per level on every GPS fix.
 */
@Immutable
sealed interface BoundaryMode {

    /** Countries. Used by the continent level (nothing selected) and the country level. */
    data object Countries : BoundaryMode

    /**
     * Regions of one country. Country outlines stay visible as thin context only.
     * @param countryIso2 restricts which regions are drawn at all.
     */
    data class Regions(val countryIso2: String) : BoundaryMode
}

/** One tap resolved against the rendered boundary layers. */
@Immutable
data class BoundaryTap(
    val featureId: String,
    val name: String,
    val countryIso2: String?,
    /** Continent the tapped country belongs to, straight from the tile. */
    val continentId: String? = null,
)

@Immutable
data class BoundaryMapState(
    val mode: BoundaryMode = BoundaryMode.Countries,
    val selectedId: String? = null,
    /**
     * Restricts which countries exist on screen at all. At the continent level the rest of the
     * world is not dimmed but filtered out entirely — only this continent over open ocean.
     */
    val continentId: String? = null,
)
