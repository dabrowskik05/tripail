package com.tripex.pose.data.location

import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.LocationConfig
import com.tripex.pose.domain.location.LocationTracker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
internal class FusedLocationTracker @Inject constructor(
    private val clientProvider: FusedLocationClientProvider,
) : LocationTracker {

    override fun locationUpdates(config: LocationConfig): Flow<DomainLocation> = callbackFlow {
        val client = clientProvider.get()
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            config.intervalMs,
        )
            .setMinUpdateIntervalMillis(config.fastestIntervalMs)
            .setMinUpdateDistanceMeters(config.minDistanceMeters)
            .setMaxUpdateDelayMillis(config.maxUpdateDelayMs)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    trySend(location.toDomainLocation())
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (security: SecurityException) {
            close(security)
            return@callbackFlow
        }

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}

internal fun Location.toDomainLocation(): DomainLocation =
    DomainLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        },
    )
