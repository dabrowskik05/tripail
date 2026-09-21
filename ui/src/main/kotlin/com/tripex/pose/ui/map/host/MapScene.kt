package com.tripex.pose.ui.map.host

import androidx.compose.runtime.Immutable
import com.tripex.pose.domain.geo.GeoBounds

/**
 * What the one map should be showing right now.
 *
 * A scene is pure description — it says which boundaries matter and what is selected, and says
 * nothing about the camera. Moving the camera is a separate, explicit act
 * ([MapHostState.flyTo]), because a selection that quietly re-framed the view was the single
 * most disorienting thing in the drill-down.
 */
@Immutable
data class MapScene(
    val level: MapLevel = MapLevel.World,
    /** Restricts countries to one continent; `null` shows them all. */
    val continentId: String? = null,
    /** Country whose regions are shown at [MapLevel.Region]. */
    val countryIso2: String? = null,
    /** Highlighted feature id at the current level. */
    val selectedId: String? = null,
) {
    val showsRegions: Boolean get() = level == MapLevel.Region

    /**
     * At the country level everything but the picked country sinks under denser parchment, so the
     * choice is unmistakable (vision, level 3).
     */
    val dimsOtherCountries: Boolean get() = level == MapLevel.Country && selectedId != null

    /**
     * Region outlines of the picked country are drawn as context from the moment the country is
     * opened — the player is meant to see what is inside it before tapping anything.
     */
    val showsRegionContext: Boolean
        get() = countryIso2 != null && (showsRegions || level == MapLevel.Country)
}

enum class MapLevel {
    /** Whole planet, countries tinted by continent — the entry point. */
    World,

    /** One continent, the rest of the world filtered away. */
    Continent,

    /** Countries again, with one of them picked out. */
    Country,

    /** Administrative regions of a single country. */
    Region,

    /** Exploring: boundaries step back, the parchment and the live map are the subject. */
    Explore,
}

/** One-shot request to move the camera. Consumed by the surface, never re-applied. */
@Immutable
data class CameraRequest(
    val bounds: GeoBounds,
    val animate: Boolean,
    /** Distinguishes two requests for the same bounds. */
    val token: Long,
)
