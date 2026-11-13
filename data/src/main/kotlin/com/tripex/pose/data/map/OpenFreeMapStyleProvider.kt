package com.tripex.pose.data.map

import com.tripex.pose.domain.map.MapStyleProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Basemap from OpenFreeMap — free, no API key, no request quota.
 *
 * The tiles follow the OpenMapTiles schema: settlements sit in the `place` source layer with a
 * `class` (city, town, village…) and carry `name:pl` / `name:en`, which is exactly what the label
 * language switch and the tap-a-city hit test read. Swapping the style keeps both working.
 */
@Singleton
internal class OpenFreeMapStyleProvider @Inject constructor() : MapStyleProvider {

    override fun styleUri(): String = STYLE_URI

    companion object {
        const val STYLE_URI: String = "https://tiles.openfreemap.org/styles/liberty"
    }
}
