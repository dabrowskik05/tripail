package com.tripex.pose.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MapTilerResponseDto(
    val features: List<MapTilerFeatureDto> = emptyList(),
)

@Serializable
internal data class MapTilerFeatureDto(
    val id: String? = null,
    @SerialName("place_name") val placeName: String? = null,
    val text: String? = null,
    /** `[west, south, east, north]` when the provider knows the extent. */
    val bbox: List<Double>? = null,
    /** `[lng, lat]`. */
    val center: List<Double>? = null,
    @SerialName("place_type") val placeType: List<String?> = emptyList(),
    val properties: MapTilerPropertiesDto? = null,
    val context: List<MapTilerContextDto> = emptyList(),
)

@Serializable
internal data class MapTilerPropertiesDto(
    val kind: String? = null,
    /**
     * Nullable elements on purpose: landforms ("Norwegian Lake") arrive as `[null]`. A strict
     * `List<String>` failed the **whole** response on that one feature — full Polish names pull
     * such features in, so they found nothing while their prefixes and English names worked.
     */
    @SerialName("place_type_name") val placeTypeName: List<String?> = emptyList(),
    /** ISO 3166-1 alpha-2, lowercase. */
    @SerialName("country_code") val countryCode: String? = null,
)

/** Parent areas — region, country. Used to tell homonyms apart without extra requests. */
@Serializable
internal data class MapTilerContextDto(
    val id: String? = null,
    val text: String? = null,
)
