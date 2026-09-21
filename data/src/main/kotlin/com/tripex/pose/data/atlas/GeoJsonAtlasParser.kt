package com.tripex.pose.data.atlas

import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.atlas.ContinentShape
import com.tripex.pose.domain.geo.atlas.LandShape
import com.tripex.pose.domain.geo.atlas.Ring
import com.tripex.pose.domain.geo.projection.GeometryOps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses overview atlas GeoJSON (`continents.geojson`) into domain shapes.
 * Coordinates are [lng, lat] — same convention as [com.tripex.pose.domain.geo.FogGeoJsonBuilder].
 */
class GeoJsonAtlasParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    data class ParsedAtlas(
        val land: LandShape,
        val continents: List<ContinentShape>,
    )

    fun parse(geoJson: String): ParsedAtlas {
        val root = json.parseToJsonElement(geoJson).jsonObject
        val features = root["features"]?.jsonArray.orEmpty()
        val byContinent = LinkedHashMap<ContinentId, MutableList<Ring>>()
        val allRings = ArrayList<Ring>()

        for (feature in features) {
            val obj = feature.jsonObject
            val continentRaw = obj["properties"]?.jsonObject
                ?.get("continent")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: continue
            val continentId = runCatching { ContinentId.valueOf(continentRaw) }.getOrNull()
                ?: continue
            val geometry = obj["geometry"]?.jsonObject ?: continue
            val rings = ringsFromGeometry(geometry)
            if (rings.isEmpty()) continue
            byContinent.getOrPut(continentId) { ArrayList() }.addAll(rings)
            allRings.addAll(rings)
        }

        require(allRings.isNotEmpty()) { "Atlas GeoJSON contains no land rings" }

        val continents = byContinent.map { (id, rings) ->
            ContinentShape(
                id = id,
                rings = rings,
                bounds = GeometryOps.boundsOf(rings),
            )
        }
        return ParsedAtlas(
            land = LandShape(rings = allRings, bounds = GeometryOps.boundsOf(allRings)),
            continents = continents,
        )
    }

    private fun ringsFromGeometry(geometry: JsonObject): List<Ring> {
        val type = geometry["type"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
        val coordinates = geometry["coordinates"] ?: return emptyList()
        return when (type) {
            "Polygon" -> polygonRings(coordinates.jsonArray)
            "MultiPolygon" -> {
                val out = ArrayList<Ring>()
                for (polygon in coordinates.jsonArray) {
                    out += polygonRings(polygon.jsonArray)
                }
                out
            }
            else -> emptyList()
        }
    }

    /** Exterior + holes as separate rings (Canvas / mask consumers treat them as filled paths). */
    private fun polygonRings(coordinates: JsonArray): List<Ring> =
        coordinates.mapNotNull { ringElement -> ringFrom(ringElement) }

    private fun ringFrom(element: JsonElement): Ring? {
        val points = element.jsonArray
        if (points.size < 3) return null
        return points.map { point ->
            val coords = point.jsonArray
            require(coords.size >= 2) { "Coordinate pair required" }
            coords[0].jsonPrimitive.double to coords[1].jsonPrimitive.double
        }
    }
}
