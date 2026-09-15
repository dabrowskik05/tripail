package com.tripex.pose.data.map

import com.tripex.pose.data.BuildConfig
import com.tripex.pose.domain.map.MapStyleProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MapTilerStyleProvider @Inject constructor() : MapStyleProvider {

    override fun styleUri(): String {
        val key = BuildConfig.MAPTILER_API_KEY.trim()
        return if (key.isEmpty()) {
            DEMO_STYLE_URI
        } else {
            "https://api.maptiler.com/maps/$MAP_ID/style.json?key=$key"
        }
    }

    companion object {
        private const val MAP_ID = "streets-v2"
        /** Offline-friendly fallback when MAPTILER_API_KEY is missing. */
        const val DEMO_STYLE_URI: String = "https://demotiles.maplibre.org/style.json"
    }
}
