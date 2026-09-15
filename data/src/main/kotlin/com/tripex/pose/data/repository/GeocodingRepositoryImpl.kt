package com.tripex.pose.data.repository

import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.data.mapper.toPlace
import com.tripex.pose.data.network.NominatimApi
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Nominatim-backed geocoding with in-memory cache and ≤1 req/s pacing.
 */
@Singleton
internal class GeocodingRepositoryImpl @Inject constructor(
    private val api: NominatimApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GeocodingRepository {

    private val cache = ConcurrentHashMap<String, Place>()
    private val rateMutex = Mutex()
    private var lastRequestAtMs: Long = 0L

    override suspend fun search(query: String): Result<Place> = withContext(ioDispatcher) {
        val key = query.trim().lowercase()
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty query"))
        }
        cache[key]?.let { return@withContext Result.success(it) }

        rateMutex.withLock {
            cache[key]?.let { return@withLock Result.success(it) }

            val elapsed = System.currentTimeMillis() - lastRequestAtMs
            val waitMs = MIN_INTERVAL_MS - elapsed
            if (waitMs > 0) delay(waitMs)

            runCatching {
                val results = api.search(query = query.trim())
                lastRequestAtMs = System.currentTimeMillis()
                val dto = results.firstOrNull()
                    ?: throw NoSuchElementException("No results for \"$query\"")
                val place = dto.toPlace()
                cache[key] = place
                place
            }
        }
    }

    companion object {
        /** Nominatim policy: absolute max 1 req/s — pad slightly. */
        const val MIN_INTERVAL_MS: Long = 1_100L
    }
}
