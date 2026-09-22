package com.tripex.pose.ui.explore

import androidx.compose.runtime.Immutable
import com.tripex.pose.domain.geo.atlas.AdminLevel

/**
 * What the shared boundary surface is currently showing.
 *
 * Switching [BoundaryMapState.selectedId] is a filter change on layers that already exist — never
 * a new source, a rebuilt style or fresh geometry.
 *
 * It deliberately holds **no map data**. The style URI, the boundary tiles and the parchment
 * geometry belong to the single map host above the navigation graph.
 *
 * `BoundaryMode` used to live here and is gone (V3.3.1): the country level no longer toggles
 * between "tap regions" and "tap countries". Both are live, and a tap is resolved by what it
 * actually hit.
 */
@Immutable
data class BoundaryTap(
    val featureId: String,
    val name: String,
    /** Which layer the hit came from — a region of a country, or a country itself. */
    val level: AdminLevel,
    val countryIso2: String?,
    /** Continent the tapped country belongs to, straight from the tile. */
    val continentId: String? = null,
)

@Immutable
data class BoundaryMapState(
    val selectedId: String? = null,
    /**
     * Restricts which countries exist on screen at all. At the continent level the rest of the
     * world is not dimmed but filtered out entirely — only this continent over open ocean.
     */
    val continentId: String? = null,
)
