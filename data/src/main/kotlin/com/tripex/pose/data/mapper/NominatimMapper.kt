package com.tripex.pose.data.mapper

import com.tripex.pose.data.network.NominatimPlaceDto
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place

internal fun NominatimPlaceDto.toPlace(): Place =
    Place(
        displayName = displayName,
        latitude = lat.toDouble(),
        longitude = lon.toDouble(),
        boundingBox = boundingbox?.toGeoBoundsOrNull(),
    )

/**
 * Nominatim boundingbox format: [lat_min, lat_max, lon_min, lon_max].
 */
internal fun List<String>.toGeoBoundsOrNull(): GeoBounds? {
    if (size < 4) return null
    return runCatching {
        GeoBounds(
            south = this[0].toDouble(),
            north = this[1].toDouble(),
            west = this[2].toDouble(),
            east = this[3].toDouble(),
        )
    }.getOrNull()
}
