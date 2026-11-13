package com.tripex.pose.data.mapper

import com.tripex.pose.data.network.PhotonFeatureDto
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind

private const val EXTENT_SIZE = 4
private const val CENTER_SIZE = 2

/**
 * Photon feature → domain [Place].
 *
 * The kind drives the reveal radius (a village must not uncover as much as a capital) and the
 * automatic city unlock, so it is read from the OSM tag first and Photon's coarser layer second.
 */
internal fun PhotonFeatureDto.toPlace(): Place? {
    val props = properties ?: return null
    val center = geometry?.coordinates?.takeIf { it.size >= CENTER_SIZE } ?: return null
    val name = props.name?.takeIf { it.isNotBlank() } ?: return null
    return Place(
        displayName = name,
        // GeoJSON order is [lng, lat].
        latitude = center[1],
        longitude = center[0],
        boundingBox = props.extent?.takeIf { it.size >= EXTENT_SIZE }?.let {
            // Photon's extent is [minLon, maxLat, maxLon, minLat].
            GeoBounds(north = it[1], south = it[3], east = it[2], west = it[0])
        },
        kind = placeKind(),
        id = if (props.osmType != null && props.osmId != null) {
            "osm:${props.osmType}${props.osmId}"
        } else {
            "$name@${center[0]},${center[1]}"
        },
        // Parent areas tell homonyms apart ("Rzym, Polska" vs "Roma, Italia") with no extra request.
        context = listOfNotNull(props.county, props.state, props.country)
            .filter { it.isNotBlank() && it != name }
            .distinct(),
        countryCode = props.countryCode?.takeIf { it.isNotBlank() }?.uppercase(),
    )
}

private fun PhotonFeatureDto.placeKind(): PlaceKind {
    val key = properties?.osmKey?.lowercase()
    val value = properties?.osmValue?.lowercase()
    val layer = properties?.type?.lowercase()
    if (key == "place") {
        when (value) {
            "country" -> return PlaceKind.Country
            "state", "region", "province" -> return PlaceKind.Region
            "city" -> return PlaceKind.City
            "town" -> return PlaceKind.Town
            "municipality" -> return PlaceKind.Municipality
            "village", "hamlet", "isolated_dwelling" -> return PlaceKind.Village
        }
    }
    return when (layer) {
        "country" -> PlaceKind.Country
        "state" -> PlaceKind.Region
        // Photon files every settlement — city, town, village — under the `city` layer.
        "city" -> PlaceKind.City
        "county" -> PlaceKind.Municipality
        "district", "locality" -> PlaceKind.Village
        "street", "house" -> if (key == null || key == "highway" || key == "building" || key == "place") {
            PlaceKind.Address
        } else {
            PlaceKind.Poi
        }
        else -> if (key != null && key != "boundary") PlaceKind.Poi else PlaceKind.Unknown
    }
}
