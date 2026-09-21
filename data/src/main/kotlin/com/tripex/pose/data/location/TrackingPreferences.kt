package com.tripex.pose.data.location

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.tripex.pose.core.di.IoDispatcher
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.location.DwellDetector
import com.tripex.pose.domain.location.TrackingIntentRepository
import com.tripex.pose.domain.location.TrackingSession
import com.tripex.pose.domain.location.TrackingSessionRepository
import com.tripex.pose.domain.location.TrackingSetupRepository
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class TrackingPrefs

internal object TrackingDataStoreFactory {
    private const val NAME = "tracking_prefs"

    fun create(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(NAME) },
        )
}

/**
 * The player's standing answer to "do you want to be tracked?" (V3.7.2).
 *
 * Kept in its own DataStore file rather than alongside the boundary bookkeeping, because the
 * tracking service reads this on a cold start — possibly before anything else in the app has
 * been touched — and a file that only ever holds three keys opens faster than one that does not.
 */
@Singleton
internal class TrackingIntentDataStore
    @Inject
    constructor(
        @TrackingPrefs private val dataStore: DataStore<Preferences>,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val logger: Logger,
    ) : TrackingIntentRepository {

        override suspend fun set(requested: Boolean) {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it[REQUESTED] = requested } }
                    .onFailure { logger.e(TAG, "Could not persist tracking intent", it) }
            }
        }

        /**
         * Defaults to `false`. A read that fails must not resurrect tracking the player switched
         * off — the safe answer to "I cannot tell" is to stay quiet.
         */
        override suspend fun isRequested(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.data.first()[REQUESTED] == true }
                    .getOrElse {
                        logger.e(TAG, "Could not read tracking intent", it)
                        false
                    }
            }

        override fun observe(): Flow<Boolean> =
            dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .map { it[REQUESTED] == true }

        private companion object {
            const val TAG = "TrackingIntent"
            val REQUESTED = booleanPreferencesKey("tracking_requested")
        }
    }

/**
 * One overwritten record: last accepted fix + dwell anchor (V3.7.3).
 *
 * Flat keys rather than a serialised blob on purpose — this is written on every accepted fix, and
 * a handful of primitives costs less than encoding a document each time. It is also, deliberately,
 * incapable of growing into a route log (decision §8.2 pkt 9): there is exactly one slot.
 */
@Singleton
internal class TrackingSessionDataStore
    @Inject
    constructor(
        @TrackingPrefs private val dataStore: DataStore<Preferences>,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val logger: Logger,
    ) : TrackingSessionRepository {

        override suspend fun load(): TrackingSession =
            withContext(ioDispatcher) {
                runCatching {
                    val prefs = dataStore.data.first()
                    TrackingSession(
                        lastFix = prefs.fix(),
                        dwell = prefs.dwell(),
                    )
                }.getOrElse {
                    logger.e(TAG, "Could not read tracking session", it)
                    TrackingSession.EMPTY
                }
            }

        override suspend fun save(session: TrackingSession) {
            withContext(ioDispatcher) {
                runCatching {
                    dataStore.edit { prefs ->
                        session.lastFix.let { fix ->
                            if (fix == null) {
                                prefs.remove(FIX_LAT)
                                prefs.remove(FIX_LNG)
                                prefs.remove(FIX_AT)
                            } else {
                                prefs[FIX_LAT] = fix.latitude
                                prefs[FIX_LNG] = fix.longitude
                                prefs[FIX_AT] = fix.atMs
                            }
                        }
                        session.dwell.let { dwell ->
                            if (dwell == null) {
                                prefs.remove(DWELL_LAT)
                                prefs.remove(DWELL_LNG)
                                prefs.remove(DWELL_SINCE)
                                prefs.remove(DWELL_REPORTED)
                            } else {
                                prefs[DWELL_LAT] = dwell.anchorLat
                                prefs[DWELL_LNG] = dwell.anchorLng
                                prefs[DWELL_SINCE] = dwell.sinceMs
                                prefs[DWELL_REPORTED] = dwell.reported
                            }
                        }
                    }
                }.onFailure {
                    // A lost write costs one bridge segment. Crashing the location collector
                    // would cost the rest of the trip.
                    logger.w(TAG, "Could not persist tracking session: ${it.message}")
                }
            }
        }

        override suspend fun clear() {
            withContext(ioDispatcher) {
                runCatching {
                    dataStore.edit { prefs ->
                        prefs.remove(FIX_LAT)
                        prefs.remove(FIX_LNG)
                        prefs.remove(FIX_AT)
                        prefs.remove(DWELL_LAT)
                        prefs.remove(DWELL_LNG)
                        prefs.remove(DWELL_SINCE)
                        prefs.remove(DWELL_REPORTED)
                    }
                }.onFailure { logger.w(TAG, "Could not clear tracking session: ${it.message}") }
            }
        }

        private fun Preferences.fix(): TrackingSession.Fix? {
            val lat = this[FIX_LAT] ?: return null
            val lng = this[FIX_LNG] ?: return null
            val at = this[FIX_AT] ?: return null
            return TrackingSession.Fix(latitude = lat, longitude = lng, atMs = at)
        }

        private fun Preferences.dwell(): DwellDetector.State? {
            val lat = this[DWELL_LAT] ?: return null
            val lng = this[DWELL_LNG] ?: return null
            val since = this[DWELL_SINCE] ?: return null
            return DwellDetector.State(
                anchorLat = lat,
                anchorLng = lng,
                sinceMs = since,
                reported = this[DWELL_REPORTED] == true,
            )
        }

        private companion object {
            const val TAG = "TrackingSession"

            val FIX_LAT = doublePreferencesKey("last_fix_lat")
            val FIX_LNG = doublePreferencesKey("last_fix_lng")
            val FIX_AT = longPreferencesKey("last_fix_at")

            val DWELL_LAT = doublePreferencesKey("dwell_anchor_lat")
            val DWELL_LNG = doublePreferencesKey("dwell_anchor_lng")
            val DWELL_SINCE = longPreferencesKey("dwell_since")
            val DWELL_REPORTED = booleanPreferencesKey("dwell_reported")
        }
    }

/**
 * One flag, one purpose: has the player already seen the reliability screen (V3.7.5).
 *
 * A failed read reports `false`, which shows the prompt one extra time. The opposite default
 * would silently skip the screen that keeps tracking alive on aggressive ROMs — of the two ways
 * to be wrong, an extra screen is the cheap one.
 */
@Singleton
internal class TrackingSetupDataStore
    @Inject
    constructor(
        @TrackingPrefs private val dataStore: DataStore<Preferences>,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val logger: Logger,
    ) : TrackingSetupRepository {

        override suspend fun hasSeenReliabilityPrompt(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.data.first()[SEEN] == true }
                    .getOrElse {
                        logger.w(TAG, "Could not read setup flag: ${it.message}")
                        false
                    }
            }

        override suspend fun markReliabilityPromptSeen() {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it[SEEN] = true } }
                    .onFailure { logger.w(TAG, "Could not persist setup flag: ${it.message}") }
            }
        }

        private companion object {
            const val TAG = "TrackingSetup"
            val SEEN = booleanPreferencesKey("seen_reliability_prompt")
        }
    }
