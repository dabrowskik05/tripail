package com.tripex.pose.domain.usecase

import com.tripex.pose.domain.geo.BoundaryMatcher
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.geo.RevealRadiusPolicy
import com.tripex.pose.domain.geo.atlas.AdminLevel
import com.tripex.pose.domain.geo.atlas.BoundaryGeometrySource
import com.tripex.pose.domain.geo.projection.GeometryOps
import com.tripex.pose.domain.repository.UnlockedPlaceRepository
import com.tripex.pose.domain.repository.UnlockedRegionRepository
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.domain.settings.AppLanguageRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What revealing this selection would actually claim. */
sealed interface RevealTarget {

    /** A region has a real outline in the bundle; its id is what gets stored. */
    data class Region(
        val level: AdminLevel,
        val featureId: String,
        val countryIso2: String?,
    ) : RevealTarget

    /** Everything settlement-sized is a circle: a centre and a radius. */
    data class Circle(val place: Place, val radiusMeters: Double) : RevealTarget

    /**
     * A country is a place to go, not a thing to own.
     *
     * Deliberately has no reveal action: claiming a whole country in one tap empties the game of
     * the thing it is about. Picking one navigates into it instead.
     */
    data class Country(
        val iso2: String,
        val bounds: GeoBounds,
        val continentId: String,
    ) : RevealTarget
}

/**
 * What the player picked, and what could be done about it — **without doing any of it**.
 */
data class SearchSelection(
    val label: String,
    val kind: PlaceKind,
    val context: List<String>,
    val latitude: Double,
    val longitude: Double,
    val bounds: GeoBounds?,
    val target: RevealTarget,
    val isRevealed: Boolean,
    /** False for ground that was earned rather than chosen, and for countries. */
    val canCover: Boolean,
)

/**
 * Turns a picked suggestion into a described selection (V3.4.4).
 *
 * ### This use case no longer unlocks anything
 *
 * Its predecessor wrote to the database the instant a suggestion was tapped, so searching for a
 * place to *look* at silently claimed it. Searching is now a way of finding somewhere: the camera
 * goes there, a panel describes it, and revealing it is a separate, deliberate decision made by
 * pressing a button that says so.
 *
 * Nothing here writes. That is the whole contract, and the tests assert it.
 */
class ResolveSearchSelectionUseCase
    @Inject
    constructor(
        private val boundaryMatcher: BoundaryMatcher,
        private val boundaries: BoundaryGeometrySource,
        private val regions: UnlockedRegionRepository,
        private val places: UnlockedPlaceRepository,
        private val appLanguage: AppLanguageRepository,
    ) {
        suspend operator fun invoke(place: Place): Result<SearchSelection> = runCatching {
            // The bundle carries a name per language; which one the panel shows is an interface
            // decision, so it is read here rather than baked into the feature (V3.5.6).
            val language = appLanguage.observe().first()
            when (place.kind) {
                PlaceKind.Country -> resolveCountry(place, language)
                PlaceKind.Region -> resolveRegion(place, language)
                else -> resolveCircle(place)
            }
        }

        private suspend fun resolveCountry(place: Place, language: AppLanguage): SearchSelection {
            val feature = boundaryMatcher.match(AdminLevel.Adm0, place)
                ?: return resolveCircle(place)
            val bounds = boundsOf(AdminLevel.Adm0, feature.id)
                ?: place.boundingBox
                ?: return resolveCircle(place)
            return SearchSelection(
                label = feature.nameIn(language).ifBlank { place.displayName },
                kind = PlaceKind.Country,
                context = place.context,
                latitude = place.latitude,
                longitude = place.longitude,
                bounds = bounds,
                target = RevealTarget.Country(
                    iso2 = feature.id,
                    bounds = bounds,
                    continentId = feature.continentId.orEmpty(),
                ),
                isRevealed = false,
                canCover = false,
            )
        }

        private suspend fun resolveRegion(place: Place, language: AppLanguage): SearchSelection {
            // No match in the bundle degrades to a circle rather than doing nothing: the player
            // still gets something they can act on, just an approximate one.
            val feature = boundaryMatcher.match(AdminLevel.Adm1, place) ?: return resolveCircle(place)
            val revealed = regions.isUnlocked(AdminLevel.Adm1, feature.id)
            return SearchSelection(
                label = feature.nameIn(language).ifBlank { place.displayName },
                kind = PlaceKind.Region,
                context = place.context,
                latitude = place.latitude,
                longitude = place.longitude,
                bounds = boundsOf(AdminLevel.Adm1, feature.id) ?: place.boundingBox,
                target = RevealTarget.Region(
                    level = AdminLevel.Adm1,
                    featureId = feature.id,
                    countryIso2 = feature.countryIso2,
                ),
                isRevealed = revealed,
                canCover = revealed,
            )
        }

        private suspend fun resolveCircle(place: Place): SearchSelection {
            val id = place.stableId()
            val existing = places.find(id)
            return SearchSelection(
                label = place.displayName,
                kind = place.kind,
                context = place.context,
                latitude = place.latitude,
                longitude = place.longitude,
                bounds = place.boundingBox,
                target = RevealTarget.Circle(place, RevealRadiusPolicy.radiusMeters(place)),
                isRevealed = existing != null,
                // Ground earned by standing in it is not the player's to hand back.
                canCover = existing?.source == UnlockedPlaceRepository.Source.Manual,
            )
        }

        private suspend fun boundsOf(level: AdminLevel, id: String): GeoBounds? =
            boundaries.rings(level, id).getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { GeometryOps.boundsOf(it) }

        private fun Place.stableId(): String =
            id.ifBlank { "$displayName@$latitude,$longitude" }
    }
