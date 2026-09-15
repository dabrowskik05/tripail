package com.tripex.pose.domain.map

/**
 * Provides the MapLibre style URI (tile provider key stays in `:data` BuildConfig).
 */
interface MapStyleProvider {
    fun styleUri(): String
}
