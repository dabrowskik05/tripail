package com.tripex.pose

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.tripex.pose.domain.geo.atlas.AtlasRepository
import com.tripex.pose.domain.tiles.PmTilesBootstrap
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

@HiltAndroidApp
class TripexPoseApplication : Application(), ComponentCallbacks2 {

    @Inject lateinit var atlasRepository: AtlasRepository
    @Inject lateinit var pmTilesBootstrap: PmTilesBootstrap
    @Inject lateinit var appLanguageApplier: com.tripex.pose.settings.AppLanguageApplier

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Before anything draws: the language has to be in place for the first frame, not
        // applied after it and cause a visible re-layout.
        appLanguageApplier.start(applicationScope)
        applicationScope.launch {
            pmTilesBootstrap.ensureReady()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            atlasRepository.clearCache()
        }
    }

    /**
     * Must call through: a locale change arrives as a configuration change, and swallowing it
     * keeps components holding resources from the previous language (V3.5.3).
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        @Suppress("DEPRECATION")
        super.onLowMemory()
        atlasRepository.clearCache()
    }
}
