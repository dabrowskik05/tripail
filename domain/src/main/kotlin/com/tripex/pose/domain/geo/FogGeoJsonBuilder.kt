package com.tripex.pose.domain.geo

import javax.inject.Inject

/**
 * Builds a FeatureCollection for the parchment reveal wash FillLayer.
 *
 * - One world polygon with unlocked H3 outer rings as holes (vivid basemap shows through).
 * - Inner rings from H3 multipolygons (unexplored pockets) become separate wash-island Features.
 * - Whole-region unlocks arrive as `extraHoles` and are punched into the same world polygon.
 */
class FogGeoJsonBuilder
    @Inject
    constructor() {
        /** Full-world wash with no holes — shown before the first Room emission. */
        fun emptyWorld(): String = build(FogGeometry.EMPTY)

        /**
         * @param extraHoles outlines of macro-scale unlocks (whole regions). They are punched into
         *   the same world polygon as the H3 cells, so a region and a walked street behave
         *   identically from the renderer's point of view.
         */
        fun build(
            outline: FogGeometry,
            extraHoles: List<List<Pair<Double, Double>>> = emptyList(),
        ): String {
            val holes = ArrayList<List<Pair<Double, Double>>>(outline.polygons.size)
            val islands = ArrayList<List<Pair<Double, Double>>>(4)

            for (polygon in outline.polygons) {
                if (polygon.isEmpty()) continue
                holes += polygon.first()
                for (index in 1 until polygon.size) {
                    islands += polygon[index]
                }
            }

            val features =
                buildString {
                    append(polygonFeature(listOf(WORLD_RING) + holes + extraHoles))
                    for (island in islands) {
                        append(',')
                        append(polygonFeature(listOf(island)))
                    }
                }
            return """{"type":"FeatureCollection","features":[$features]}"""
        }

        private fun polygonFeature(rings: List<List<Pair<Double, Double>>>): String {
            val coordinates =
                rings.joinToString(separator = ",", prefix = "[", postfix = "]") { ring ->
                    ring.joinToString(separator = ",", prefix = "[", postfix = "]") { (lng, lat) ->
                        "[$lng,$lat]"
                    }
                }
            return """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":$coordinates}}"""
        }

        companion object {
            /** Web Mercator safe limits — ±90° breaks the tessellator. */
            val WORLD_RING: List<Pair<Double, Double>> =
                listOf(
                    -180.0 to -85.05112878,
                    180.0 to -85.05112878,
                    180.0 to 85.05112878,
                    -180.0 to 85.05112878,
                    -180.0 to -85.05112878,
                )
        }
    }
