package com.tripex.pose.domain.geo.atlas

import com.tripex.pose.domain.geo.ContinentId

/**
 * Stable identifier for a navigable / statistics area.
 *
 * Two serialized forms, both primitive-only (M2.4):
 * - [toArgs] / [fromArgs] — pair `areaKind` + `areaId`, used as navigation arguments,
 * - [toNavArg] / [fromNavArg] — single `"<kind>:<id>"` string, used in logs and caches.
 */
sealed interface AreaKey {
    data class Continent(val id: ContinentId) : AreaKey
    data class Country(val iso2: String) : AreaKey
    data class Region(val id: String) : AreaKey
    data class City(val placeId: String) : AreaKey

    /** Primitive navigation argument pair. */
    data class Args(val kind: String, val id: String)

    fun toArgs(): Args = when (this) {
        is Continent -> Args(KIND_CONTINENT, id.name)
        is Country -> Args(KIND_COUNTRY, iso2.uppercase())
        is Region -> Args(KIND_REGION, id)
        is City -> Args(KIND_CITY, placeId)
    }

    fun toNavArg(): String = with(toArgs()) { "$kind$SEPARATOR$id" }

    companion object {
        const val KIND_CONTINENT = "continent"
        const val KIND_COUNTRY = "country"
        const val KIND_REGION = "region"
        const val KIND_CITY = "city"

        private const val SEPARATOR = ":"

        fun fromArgs(kind: String, id: String): AreaKey = when (kind) {
            KIND_CONTINENT -> Continent(ContinentId.valueOf(id))
            KIND_COUNTRY -> {
                require(id.length == 2) { "Country ISO2 must be 2 chars: $id" }
                Country(id.uppercase())
            }
            KIND_REGION -> Region(id)
            KIND_CITY -> City(id)
            else -> error("Unknown AreaKey kind: $kind")
        }

        fun fromNavArg(arg: String): AreaKey {
            val kind = arg.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val id = arg.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (kind.isEmpty() || id.isEmpty()) error("Unknown AreaKey nav arg: $arg")
            return fromArgs(kind, id)
        }
    }
}
