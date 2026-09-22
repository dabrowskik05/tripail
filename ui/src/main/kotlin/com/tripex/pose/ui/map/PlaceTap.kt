package com.tripex.pose.ui.map

import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind

/**
 * A settlement tapped straight on the map (V3.3.4).
 *
 * The basemap gives a name, a point and a rough class — no bounding box, so there is nothing to
 * measure a reveal radius from. Converting to a [Place] hands that decision to
 * `RevealRadiusPolicy`, exactly as a geocoder result would: the radius is policy, never a
 * constant at a call site.
 */
data class PlaceTap(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** MapTiler's own word for the settlement size: city, town, village, suburb… */
    val placeClass: String?,
) {
    fun toPlace(): Place = Place(
        displayName = name,
        latitude = latitude,
        longitude = longitude,
        // No bbox from a label; the kind is what the radius policy falls back to.
        boundingBox = null,
        kind = kind(),
        id = "map:$name@$latitude,$longitude",
    )

    private fun kind(): PlaceKind = when (placeClass?.lowercase()) {
        "city" -> PlaceKind.City
        "town" -> PlaceKind.Town
        "village", "hamlet" -> PlaceKind.Village
        "suburb", "neighbourhood", "quarter" -> PlaceKind.Town
        "country" -> PlaceKind.Country
        "state", "province", "region" -> PlaceKind.Region
        else -> PlaceKind.City
    }
}
