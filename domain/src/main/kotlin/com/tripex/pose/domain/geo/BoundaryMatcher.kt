package com.tripex.pose.domain.geo

import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryFeature
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import java.text.Normalizer
import javax.inject.Inject

/**
 * Matches a geocoder result to a feature in the local boundary bundle (M4.6).
 *
 * MapTiler ids and Natural Earth `adm1_code`s are two unrelated namespaces — comparing them
 * directly is meaningless. The only thing both sides agree on is geography, so the match starts
 * from the result's centre and is confirmed by name.
 */
class BoundaryMatcher
    @Inject
    constructor(
        private val boundaries: BoundaryGeometrySource,
    ) {
        /** @return the bundle feature containing [place], or `null` when the bundle has none. */
        suspend fun match(level: AdminLevel, place: Place): BoundaryFeature? {
            val hit = boundaries.featureAt(level, place.latitude, place.longitude) ?: return null
            // A point-in-polygon hit is already decisive; the name check only guards against a
            // centre that fell into a neighbour across a border.
            if (namesAgree(hit, place)) return hit
            return hit.takeIf { place.kind == PlaceKind.Region || place.kind == PlaceKind.Country }
        }

        private fun namesAgree(feature: BoundaryFeature, place: Place): Boolean {
            val candidates = listOfNotNull(feature.name, feature.namePl).map { it.normalized() }
            val target = place.displayName.normalized()
            return candidates.any { it == target || it.contains(target) || target.contains(it) }
        }

        /** Lowercase, diacritics stripped — "Małopolskie" and "Malopolskie" are the same place. */
        private fun String.normalized(): String =
            Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
                .replace(DIACRITICS, "")
                .replace(NON_ALPHANUMERIC, "")

        private companion object {
            val DIACRITICS = "\\p{InCombiningDiacriticalMarks}+".toRegex()
            val NON_ALPHANUMERIC = "[^a-z0-9]".toRegex()
        }
    }
