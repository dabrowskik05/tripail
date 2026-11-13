package com.tripex.pose.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PhotonResponseDto(
    val features: List<PhotonFeatureDto> = emptyList(),
)

@Serializable
internal data class PhotonFeatureDto(
    val geometry: PhotonGeometryDto? = null,
    val properties: PhotonPropertiesDto? = null,
)

@Serializable
internal data class PhotonGeometryDto(
    /** `[lng, lat]`. */
    val coordinates: List<Double> = emptyList(),
)

@Serializable
internal data class PhotonPropertiesDto(
    /** `N`, `W` or `R` — together with [osmId] a stable id across requests. */
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    /** OSM tag key, e.g. `place`, `boundary`, `amenity`. */
    @SerialName("osm_key") val osmKey: String? = null,
    /** OSM tag value, e.g. `city`, `village`, `administrative`. */
    @SerialName("osm_value") val osmValue: String? = null,
    /** Photon's own layer: `house`, `street`, `locality`, `district`, `city`, `county`, `state`, `country`, `other`. */
    val type: String? = null,
    val name: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
    /** ISO 3166-1 alpha-2, upper case. */
    @SerialName("countrycode") val countryCode: String? = null,
    /** **`[minLon, maxLat, maxLon, minLat]`** — not GeoJSON bbox order. */
    val extent: List<Double>? = null,
)
