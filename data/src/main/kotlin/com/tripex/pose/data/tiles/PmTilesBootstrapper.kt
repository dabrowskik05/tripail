package com.tripex.pose.data.tiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.data.BuildConfig
import com.tripex.pose.domain.geo.atlas.BoundaryTilesProvider
import com.tripex.pose.domain.tiles.PmTilesBootstrap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class PmTilesBootstrapper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataStore: DataStore<Preferences>,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PmTilesBootstrap {

        private val mutex = Mutex()

        override suspend fun ensureReady(): Result<String> = mutex.withLock {
            runCatching {
                withContext(ioDispatcher) {
                    val dest = destinationFile()
                    val storedVersion = dataStore.data.first()[VERSION_KEY]
                    val needsCopy = !dest.exists() ||
                        dest.length() == 0L ||
                        storedVersion != BuildConfig.BOUNDARIES_VERSION
                    if (needsCopy) {
                        copyAtomically(dest)
                        dataStore.edit { prefs ->
                            prefs[VERSION_KEY] = BuildConfig.BOUNDARIES_VERSION
                        }
                    }
                    dest.absolutePath
                }
            }
        }

        fun destinationFile(): File =
            File(File(context.filesDir, TILES_DIR), FILE_NAME)

        private fun copyAtomically(dest: File) {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, "$FILE_NAME.tmp")
            if (tmp.exists() && !tmp.delete()) {
                throw IOException("Cannot clear incomplete PMTiles temp file")
            }
            var total = 0L
            context.assets.open(ASSET_PATH).use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                    }
                }
            }
            if (total == 0L) {
                tmp.delete()
                throw IOException("PMTiles asset was empty")
            }
            if (dest.exists() && !dest.delete()) {
                tmp.delete()
                throw IOException("Cannot replace existing PMTiles file")
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                throw IOException("Failed to rename PMTiles temp file into place")
            }
        }

        companion object {
            const val ASSET_PATH = "tiles/boundaries.pmtiles"
            const val TILES_DIR = "tiles"
            const val FILE_NAME = "boundaries.pmtiles"
            val VERSION_KEY = intPreferencesKey("boundaries_version")
        }
    }

@Singleton
class BoundaryTilesProviderImpl
    @Inject
    constructor(
        private val bootstrapper: PmTilesBootstrapper,
    ) : BoundaryTilesProvider {

        override fun localFilePath(): String? =
            bootstrapper.destinationFile()
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath

        override fun styleSourceUri(): String {
            val path = localFilePath() ?: return ""
            return "pmtiles://file://$path"
        }
    }

internal object BoundariesDataStoreFactory {
    private const val NAME = "boundaries_prefs"

    fun create(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(NAME) },
        )
}
