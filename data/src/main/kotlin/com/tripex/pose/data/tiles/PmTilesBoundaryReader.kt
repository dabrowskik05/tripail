package com.tripex.pose.data.tiles

import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryFields
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.atlas.BoundaryTilesProvider
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.domain.geo.projection.WebMercator
import java.io.File
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class PmTilesBoundaryReader
    @Inject
    constructor(
        private val tilesProvider: BoundaryTilesProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BoundaryGeometrySource {

        private val cache = object : LinkedHashMap<CacheKey, List<Ring>>(CACHE_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, List<Ring>>?): Boolean =
                size > CACHE_SIZE
        }

        override suspend fun rings(level: AdminLevel, id: String): Result<List<Ring>> =
            withContext(ioDispatcher) {
                runCatching {
                    val key = CacheKey(level, id)
                    synchronized(cache) { cache[key] }?.let { return@runCatching it }

                    val path = tilesProvider.localFilePath()
                        ?: error("Boundaries PMTiles not ready")
                    val collected = PmTilesArchive(File(path)).use { archive ->
                        collectRings(archive, level, id)
                    }
                    if (collected.isEmpty()) {
                        error("No geometry for $level id=$id")
                    }
                    synchronized(cache) { cache[key] = collected }
                    collected
                }
            }

        override suspend fun feature(level: AdminLevel, id: String): BoundaryFeature? =
            withContext(ioDispatcher) {
                val path = tilesProvider.localFilePath() ?: return@withContext null
                runCatching {
                    PmTilesArchive(File(path)).use { archive ->
                        val searchZoom = SEARCH_ZOOM.coerceIn(archive.header.minZoom, archive.header.maxZoom)
                        val idField = idField(level)
                        val layerName = layerName(level)
                        for (tile in allTiles(searchZoom)) {
                            val bytes = archive.getTile(tile.z, tile.x, tile.y) ?: continue
                            val layer = MvtDecoder.decode(bytes, tile.z, tile.x, tile.y)
                                .firstOrNull { it.name == layerName }
                                ?: continue
                            val match = layer.features.firstOrNull { it.properties[idField] == id }
                                ?: continue
                            return@use match.toBoundaryFeature(level, id)
                        }
                        null
                    }
                }.getOrNull()
            }

        override suspend fun featureAt(
            level: AdminLevel,
            lat: Double,
            lng: Double,
        ): BoundaryFeature? = withContext(ioDispatcher) {
            val path = tilesProvider.localFilePath() ?: return@withContext null
            PmTilesArchive(File(path)).use { archive ->
                val zoom = COVERAGE_ZOOM.coerceIn(archive.header.minZoom, archive.header.maxZoom)
                val tile = WebMercator.lngLatToTile(lng, lat, zoom)
                val bytes = archive.getTile(tile.z, tile.x, tile.y) ?: return@use null
                val layerName = layerName(level)
                val idField = idField(level)
                val layer = MvtDecoder.decode(bytes, tile.z, tile.x, tile.y)
                    .firstOrNull { it.name == layerName }
                    ?: return@use null
                for (feature in layer.features) {
                    if (feature.rings.isEmpty()) continue
                    val hit = feature.rings.any { ring -> GeometryOps.pointInRing(ring, lng, lat) }
                    if (!hit) continue
                    val featureId = feature.properties[idField] ?: continue
                    return@use feature.toBoundaryFeature(level, featureId)
                }
                null
            }
        }

        private fun MvtDecoder.Feature.toBoundaryFeature(
            level: AdminLevel,
            id: String,
        ): BoundaryFeature = BoundaryFeature(
            level = level,
            id = id,
            name = properties[nameField(level)].orEmpty(),
            namePl = properties[namePlField(level)],
            countryIso2 = properties[BoundaryFields.ADM1_COUNTRY],
            continentId = properties[BoundaryFields.ADM0_CONTINENT],
        )

        private fun nameField(level: AdminLevel): String = when (level) {
            AdminLevel.Adm0 -> BoundaryFields.ADM0_NAME
            AdminLevel.Adm1 -> BoundaryFields.ADM1_NAME
        }

        private fun namePlField(level: AdminLevel): String = when (level) {
            AdminLevel.Adm0 -> BoundaryFields.ADM0_NAME_PL
            AdminLevel.Adm1 -> BoundaryFields.ADM1_NAME_PL
        }

        private fun collectRings(
            archive: PmTilesArchive,
            level: AdminLevel,
            id: String,
        ): List<Ring> {
            val idField = idField(level)
            val layerName = layerName(level)
            val coverageZoom = COVERAGE_ZOOM.coerceIn(archive.header.minZoom, archive.header.maxZoom)
            val searchZoom = SEARCH_ZOOM.coerceIn(archive.header.minZoom, coverageZoom)

            val seedRings = ArrayList<Ring>()
            for (tile in allTiles(searchZoom)) {
                seedRings += ringsInTile(archive, tile, layerName, idField, id)
            }
            if (seedRings.isEmpty()) return emptyList()

            val bounds = GeometryOps.boundsOf(seedRings)
            val collected = ArrayList<Ring>()
            val coverageTiles = WebMercator.tilesCovering(
                west = bounds.west,
                south = bounds.south,
                east = bounds.east,
                north = bounds.north,
                zoom = coverageZoom,
            )
            for (tile in coverageTiles) {
                collected += ringsInTile(archive, tile, layerName, idField, id)
            }
            return collected.ifEmpty { seedRings }
        }

        private fun ringsInTile(
            archive: PmTilesArchive,
            tile: WebMercator.TileCoord,
            layerName: String,
            idField: String,
            id: String,
        ): List<Ring> {
            val bytes = archive.getTile(tile.z, tile.x, tile.y) ?: return emptyList()
            val layer = MvtDecoder.decode(bytes, tile.z, tile.x, tile.y)
                .firstOrNull { it.name == layerName }
                ?: return emptyList()
            return layer.features
                .filter { it.properties[idField] == id }
                .flatMap { it.rings }
        }

        private fun allTiles(zoom: Int): List<WebMercator.TileCoord> {
            val n = 1 shl zoom
            val tiles = ArrayList<WebMercator.TileCoord>(n * n)
            for (x in 0 until n) {
                for (y in 0 until n) {
                    tiles += WebMercator.TileCoord(zoom, x, y)
                }
            }
            return tiles
        }

        private fun idField(level: AdminLevel): String = when (level) {
            AdminLevel.Adm0 -> BoundaryFields.ADM0_ID
            AdminLevel.Adm1 -> BoundaryFields.ADM1_ID
        }

        private fun layerName(level: AdminLevel): String = when (level) {
            AdminLevel.Adm0 -> BoundaryFields.LAYER_ADM0
            AdminLevel.Adm1 -> BoundaryFields.LAYER_ADM1
        }

        private data class CacheKey(val level: AdminLevel, val id: String)

        companion object {
            const val COVERAGE_ZOOM = 5
            private const val SEARCH_ZOOM = 3
            private const val CACHE_SIZE = 3
        }
    }
