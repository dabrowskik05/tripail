package com.tripex.pose.domain.geo

import javax.inject.Inject

/**
 * Builds a FeatureCollection for the parchment reveal wash FillLayer.
 *
 * - One world polygon with revealed outer rings as holes (vivid basemap shows through).
 * - Inner rings (unexplored pockets enclosed by revealed ground) become separate wash Features.
 *
 * ### The input must already be unioned
 *
 * This class takes a [FogGeometry] straight from [RevealUnion] and nothing else. It used to
 * accept a second `extraHoles` list for region and city unlocks, which meant two shapes could
 * land in the same polygon as overlapping holes — and overlapping holes are undefined for the
 * tessellator, which rendered the overlap as fog. There is deliberately no longer a parameter
 * that lets a shape reach the renderer without passing through the union first.
 */
class FogGeoJsonBuilder
    @Inject
    constructor() {
        /** Full-world wash with no holes — shown before the first Room emission. */
        fun emptyWorld(): String = build(FogGeometry.EMPTY)

        /** @param outline disjoint polygons from [RevealUnion] — never raw, unmerged shapes. */
        fun build(outline: FogGeometry): String {
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
                    append(polygonFeature(listOf(WORLD_RING) + holes))
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
