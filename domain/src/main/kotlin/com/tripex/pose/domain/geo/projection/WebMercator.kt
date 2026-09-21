package com.tripex.pose.domain.geo.projection

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Web Mercator helpers for PMTiles / MVT tile coordinate transforms.
 */
object WebMercator {
    private const val MAX_LAT = 85.05112878

    data class TileCoord(val z: Int, val x: Int, val y: Int)

    fun lngLatToTile(lng: Double, lat: Double, zoom: Int): TileCoord {
        val n = 1 shl zoom
        val x = floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latClamped = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val latRad = Math.toRadians(latClamped)
        val y = floor((1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n)
            .toInt()
            .coerceIn(0, n - 1)
        return TileCoord(zoom, x, y)
    }

    /**
     * Northwest corner of tile (z, x, y) in WGS84 degrees.
     */
    fun tileToLngLat(z: Int, x: Int, y: Int): Pair<Double, Double> {
        val n = 1 shl z
        val lng = x.toDouble() / n * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * y.toDouble() / n)))
        return lng to Math.toDegrees(latRad)
    }

    /**
     * Converts MVT local tile coordinates (extent typically 4096) to lng/lat.
     */
    fun tileLocalToLngLat(
        z: Int,
        tileX: Int,
        tileY: Int,
        localX: Int,
        localY: Int,
        extent: Int,
    ): Pair<Double, Double> {
        val n = 1 shl z
        val mercX = (tileX + localX.toDouble() / extent) / n
        val mercY = (tileY + localY.toDouble() / extent) / n
        val lng = mercX * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * mercY)))
        return lng to Math.toDegrees(latRad)
    }

    /** Tiles that cover the given WGS84 bounding box at [zoom] (inclusive). */
    fun tilesCovering(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        zoom: Int,
    ): List<TileCoord> {
        val nw = lngLatToTile(west, north, zoom)
        val se = lngLatToTile(east, south, zoom)
        val tiles = ArrayList<TileCoord>((se.x - nw.x + 1) * (se.y - nw.y + 1))
        for (x in nw.x..se.x) {
            for (y in nw.y..se.y) {
                tiles += TileCoord(zoom, x, y)
            }
        }
        return tiles
    }

    private fun sinh(x: Double): Double = (exp(x) - exp(-x)) / 2.0
}
