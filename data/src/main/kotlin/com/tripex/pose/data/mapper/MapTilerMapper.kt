package com.tripex.pose.data.mapper

import com.tripex.pose.data.network.MapTilerFeatureDto
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind

private const val BBOX_SIZE = 4
private const val CENTER_SIZE = 2

/**
 * MapTiler feature → domain [Place].
 *
 * `place_type` is the categorisation the reveal radius hangs off (M4.5): a village must not
 * uncover as much ground as a capital. Unknown types fall back rather than guessing big.
 */
internal fun MapTilerFeatureDto.toPlace(): Place? {
    val center = center?.takeIf { it.size >= CENTER_SIZE } ?: return null
    val name = text ?: placeName ?: return null
    return Place(
        displayName = name,
        // GeoJSON order is [lng, lat].
        latitude = center[1],
        longitude = center[0],
        boundingBox = bbox?.takeIf { it.size >= BBOX_SIZE }?.let {
            GeoBounds(west = it[0], south = it[1], east = it[2], north = it[3])
        },
        kind = placeKind(),
        id = id ?: "$name@${center[0]},${center[1]}",
        context = context.mapNotNull { it.text }.filter { it.isNotBlank() },
        countryCode = properties?.countryCode?.takeIf { it.isNotBlank() }?.uppercase(),
    )
}

private fun MapTilerFeatureDto.placeKind(): PlaceKind {
    val types = (placeType + properties?.placeTypeName.orEmpty() + listOf(properties?.kind))
        .filterNotNull()
        .map { it.lowercase() }
    return when {
        types.any { it.contains("country") } -> PlaceKind.Country
        types.any { it.contains("region") || it.contains("state") } -> PlaceKind.Region
        types.any { it.contains("city") } -> PlaceKind.City
        types.any { it.contains("town") } -> PlaceKind.Town
        types.any { it.contains("municipal") } -> PlaceKind.Municipality
        types.any { it.contains("village") || it.contains("hamlet") } -> PlaceKind.Village
        // MapTiler's own word for a settlement is plain "place" — cities, towns and villages all
        // arrive under it. Treating it as Unknown made the automatic city unlock a silent no-op.
        types.any { it == "place" } -> PlaceKind.City
        types.any { it.contains("locality") } -> PlaceKind.Town
        types.any { it.contains("address") || it.contains("street") } -> PlaceKind.Address
        types.any { it.contains("poi") } -> PlaceKind.Poi
        else -> PlaceKind.Unknown
    }
}
