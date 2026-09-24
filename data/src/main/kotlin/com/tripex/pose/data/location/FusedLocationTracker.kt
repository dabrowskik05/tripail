package com.tripex.pose.data.location

import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.LocationConfig
import com.tripex.pose.domain.location.LocationTracker
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

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

    override suspend fun currentLocation(): DomainLocation? =
        withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationTokenSource()
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    // A fix from the last few minutes is as good as a new one for "which
                    // continent am I on", and it answers instantly.
                    .setMaxUpdateAgeMillis(CURRENT_LOCATION_MAX_AGE_MS)
                    .build()
                try {
                    clientProvider.get().getCurrentLocation(request, cancellation.token)
                        .addOnSuccessListener { location -> continuation.resume(location?.toDomainLocation()) }
                        .addOnFailureListener { continuation.resume(null) }
                } catch (security: SecurityException) {
                    continuation.resume(null)
                }
                continuation.invokeOnCancellation { cancellation.cancel() }
            }
        }

    private companion object {
        const val CURRENT_LOCATION_TIMEOUT_MS = 8_000L
        const val CURRENT_LOCATION_MAX_AGE_MS = 5 * 60_000L
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
