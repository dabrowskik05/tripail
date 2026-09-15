package com.tripex.pose.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NominatimPlaceDto(
    val lat: String,
    val lon: String,
    @SerialName("display_name") val displayName: String,
    val boundingbox: List<String>? = null,
)
