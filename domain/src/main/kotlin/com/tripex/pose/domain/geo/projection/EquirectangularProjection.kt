package com.tripex.pose.domain.geo.projection

/**
 * Equirectangular (plate carrée) projection for the overview Canvas.
 * Latitude is clamped to [[MIN_LAT], [MAX_LAT]].
 */
object EquirectangularProjection {
    const val MIN_LAT = -90.0
    const val MAX_LAT = 84.0

    fun lngLatToXy(lng: Double, lat: Double, worldWidth: Double): Pair<Double, Double> {
        val clampedLat = lat.coerceIn(MIN_LAT, MAX_LAT)
        val worldHeight = worldWidth * (MAX_LAT - MIN_LAT) / 360.0
        val x = (lng + 180.0) / 360.0 * worldWidth
        val y = (MAX_LAT - clampedLat) / (MAX_LAT - MIN_LAT) * worldHeight
        return x to y
    }

    fun xyToLngLat(x: Double, y: Double, worldWidth: Double): Pair<Double, Double> {
        val worldHeight = worldWidth * (MAX_LAT - MIN_LAT) / 360.0
        val lng = x / worldWidth * 360.0 - 180.0
        val lat = MAX_LAT - y / worldHeight * (MAX_LAT - MIN_LAT)
        return lng to lat.coerceIn(MIN_LAT, MAX_LAT)
    }
}
