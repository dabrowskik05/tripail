package com.tripex.pose.data.atlas

import android.content.res.AssetManager
import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import com.tripex.pose.domain.geo.atlas.ContinentShape
import com.tripex.pose.domain.geo.atlas.LandShape
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AtlasRepositoryImpl
    @Inject
    constructor(
        private val assetManager: AssetManager,
        private val parser: GeoJsonAtlasParser,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AtlasRepository {

        private val cache = AtomicReference<GeoJsonAtlasParser.ParsedAtlas?>(null)
        private val loadMutex = Mutex()

        override suspend fun land(): LandShape = ensureLoaded().land

        override suspend fun continents(): List<ContinentShape> = ensureLoaded().continents

        override fun clearCache() {
            cache.set(null)
        }

        private suspend fun ensureLoaded(): GeoJsonAtlasParser.ParsedAtlas {
            cache.get()?.let { return it }
            return loadMutex.withLock {
                cache.get()?.let { return it }
                val parsed = withContext(ioDispatcher) {
                    val text = assetManager.open(ASSET_PATH).bufferedReader().use { it.readText() }
                    parser.parse(text)
                }
                cache.set(parsed)
                parsed
            }
        }

        companion object {
            const val ASSET_PATH = "atlas/continents.geojson"
        }
    }
