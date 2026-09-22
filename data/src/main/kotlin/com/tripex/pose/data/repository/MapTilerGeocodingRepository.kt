package com.tripex.pose.data.repository

import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.data.BuildConfig
import com.tripex.pose.data.local.GeocodeCacheDao
import com.tripex.pose.data.local.GeocodeCacheEntity
import com.tripex.pose.data.mapper.toPlace
import com.tripex.pose.data.network.MapTilerGeocodingApi
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import com.tripex.pose.domain.settings.AppLanguageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MapTiler-backed geocoding (M4.4 / M4.7).
 *
 * Two things keep the request count down while typing:
 * - every **non-empty** result is cached in Room, so walking back a character is free and a
 *   repeated search works offline,
 * - one response is enough to disambiguate homonyms, because MapTiler ships the parent areas in
 *   `context`. There is never a follow-up request per result.
 *
 * ### Two bugs this class used to have (V3.4.2)
 *
 * **Empty answers were cached for thirty days.** "norwegia" returning nothing once meant it
 * returned nothing for a month, offline and online alike, while "norwe" kept working because it
 * had been typed on the way to a result that did come back. An absence of results is not a
 * result; it is now never written.
 *
 * **Only one language was requested**, taken from the device locale. MapTiler indexes country
 * names per language, so a Polish full name could miss entirely while the prefix matched some
 * other index. Both languages are asked for, preferred one first.
 */
@Singleton
internal class MapTilerGeocodingRepository
    @Inject
    constructor(
        private val api: MapTilerGeocodingApi,
        private val cacheDao: GeocodeCacheDao,
        private val json: Json,
        private val appLanguage: AppLanguageRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : GeocodingRepository {

        override suspend fun suggest(query: String): Result<List<Place>> =
            withContext(ioDispatcher) {
                if (query.trim().length < MIN_QUERY_LENGTH) {
                    return@withContext Result.success(emptyList())
                }
                // Keyed by language as well: the same query returns different names per language,
                // so a shared key would serve Polish results to an English UI and vice versa.
                val language = language()
                val key = cacheKey(query, language)
                readCache(key)?.let { return@withContext Result.success(it) }

                runCatching {
                    val apiKey = BuildConfig.MAPTILER_API_KEY.trim()
                    check(apiKey.isNotEmpty()) { "MAPTILER_API_KEY missing from local.properties" }
                    api.search(query = query.trim(), key = apiKey, language = language)
                        .features
                        .mapNotNull { it.toPlace() }
                        .deduplicated()
                        // An empty answer is not an answer worth remembering for a month.
                        .also { if (it.isNotEmpty()) writeCache(key, it) }
                }
            }

        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> =
            withContext(ioDispatcher) {
                runCatching {
                    val apiKey = BuildConfig.MAPTILER_API_KEY.trim()
                    check(apiKey.isNotEmpty()) { "MAPTILER_API_KEY missing from local.properties" }
                    api.reverse(lng = lng, lat = lat, key = apiKey, language = language())

                        .features
                        .firstNotNullOfOrNull { it.toPlace() }
                }
            }

        private fun cacheKey(query: String, language: String): String =
            "$language|" + query.trim().lowercase()

        /**
         * Follows the **app's** language, not the device's.
         *
         * The player can run a Polish interface on an English phone; the names in the suggestion
         * list have to match the names on the map and in the rest of the UI. Both languages are
         * sent, preferred one first — asking for one only is why a full Polish country name
         * could find nothing while its prefix worked.
         */
        private suspend fun language(): String =
            appLanguage.observe().first().geocodingLanguages

        override suspend fun search(query: String): Result<Place> =
            suggest(query).mapCatching { places ->
                places.firstOrNull() ?: throw NoSuchElementException("No results for $query")
            }

        /**
         * Providers return the same settlement at several granularities — a city, its municipality
         * and its district all named "Warszawa" a few hundred metres apart. Keeping all of them
         * makes the list useless, so entries with the same name that sit within
         * [DUPLICATE_DEGREES] of an earlier one are dropped; the first is the best ranked.
         */
        private fun List<Place>.deduplicated(): List<Place> {
            val kept = ArrayList<Place>(size)
            for (place in this) {
                val duplicate = kept.any { existing ->
                    existing.displayName.equals(place.displayName, ignoreCase = true) &&
                        abs(existing.latitude - place.latitude) < DUPLICATE_DEGREES &&
                        abs(existing.longitude - place.longitude) < DUPLICATE_DEGREES
                }
                if (!duplicate) kept += place
            }
            return kept
        }

        private suspend fun readCache(key: String): List<Place>? {
            val row = cacheDao.find(key) ?: return null
            if (System.currentTimeMillis() - row.cachedAt > CACHE_TTL_MS) return null
            return runCatching {
                json.decodeFromString<List<CachedPlace>>(row.payload).map { it.toPlace() }
            }.getOrNull()
        }

        private suspend fun writeCache(key: String, places: List<Place>) {
            runCatching {
                cacheDao.upsert(
                    GeocodeCacheEntity(
                        query = key,
                        payload = json.encodeToString(places.map { CachedPlace.of(it) }),
                        cachedAt = System.currentTimeMillis(),
                    ),
                )
                cacheDao.evictOlderThan(System.currentTimeMillis() - CACHE_TTL_MS)
            }
        }

        /** Storage shape, kept separate so the domain model can change without a migration. */
        @Serializable
        private data class CachedPlace(
            val displayName: String,
            val latitude: Double,
            val longitude: Double,
            val north: Double? = null,
            val south: Double? = null,
            val east: Double? = null,
            val west: Double? = null,
            val kind: String,
            val id: String,
            val context: List<String>,
        ) {
            fun toPlace(): Place = Place(
                displayName = displayName,
                latitude = latitude,
                longitude = longitude,
                boundingBox = if (north != null && south != null && east != null && west != null) {
                    com.tripex.pose.domain.geo.GeoBounds(north, south, east, west)
                } else {
                    null
                },
                kind = runCatching { com.tripex.pose.domain.geo.PlaceKind.valueOf(kind) }
                    .getOrDefault(com.tripex.pose.domain.geo.PlaceKind.Unknown),
                id = id,
                context = context,
            )

            companion object {
                fun of(place: Place) = CachedPlace(
                    displayName = place.displayName,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    north = place.boundingBox?.north,
                    south = place.boundingBox?.south,
                    east = place.boundingBox?.east,
                    west = place.boundingBox?.west,
                    kind = place.kind.name,
                    id = place.id,
                    context = place.context,
                )
            }
        }

        private companion object {
            const val MIN_QUERY_LENGTH = 2

            /** Roughly 5 km at mid latitudes — same place, different granularity. */
            const val DUPLICATE_DEGREES = 0.05

            const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        }
    }
