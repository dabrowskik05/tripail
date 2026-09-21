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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
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

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        @Suppress("DEPRECATION")
        super.onLowMemory()
        atlasRepository.clearCache()
    }
}
