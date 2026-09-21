package com.tripex.pose.ui.navigation

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.AreaKey
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (M2.4).
 *
 * Arguments are primitives only. A bounding box travels as four [Double]s rather than an object,
 * and an [AreaKey] as the `areaKind` + `areaId` pair, so every destination can be restored from a
 * `SavedStateHandle` without a custom `NavType`.
 *
 * Country and Region carry a bbox because a search result (M4.10) enters the hierarchy from the
 * side and has to place the camera before any boundary tile has loaded.
 */
@Serializable
data object Loading

@Serializable
data object World

@Serializable
data class Continent(val continentId: String)

@Serializable
data class Country(
    val iso2: String,
    /** Keeps the level scoped to one continent; empty means unscoped. */
    val continentId: String = "",
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

@Serializable
data class Region(
    val regionId: String,
    val countryIso2: String,
    val continentId: String = "",
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

@Serializable
data class MapView(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val areaKind: String,
    val areaId: String,
    val label: String,
)

fun Country.bounds(): GeoBounds = GeoBounds(north = north, south = south, east = east, west = west)

fun Region.bounds(): GeoBounds = GeoBounds(north = north, south = south, east = east, west = west)

fun MapView.bounds(): GeoBounds = GeoBounds(north = north, south = south, east = east, west = west)

fun MapView.areaKey(): AreaKey = AreaKey.fromArgs(areaKind, areaId)

fun countryRoute(iso2: String, bounds: GeoBounds, continentId: String = ""): Country = Country(
    iso2 = iso2.uppercase(),
    continentId = continentId,
    north = bounds.north,
    south = bounds.south,
    east = bounds.east,
    west = bounds.west,
)

fun regionRoute(
    regionId: String,
    countryIso2: String,
    bounds: GeoBounds,
    continentId: String = "",
): Region = Region(
    regionId = regionId,
    countryIso2 = countryIso2.uppercase(),
    continentId = continentId,
    north = bounds.north,
    south = bounds.south,
    east = bounds.east,
    west = bounds.west,
)

fun mapViewRoute(area: AreaKey, bounds: GeoBounds, label: String): MapView {
    val args = area.toArgs()
    return MapView(
        north = bounds.north,
        south = bounds.south,
        east = bounds.east,
        west = bounds.west,
        areaKind = args.kind,
        areaId = args.id,
        label = label,
    )
}
