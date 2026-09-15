package com.tripex.pose.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tripex.pose.BuildConfig
import com.tripex.pose.R
import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.LocationConfig
import com.tripex.pose.domain.location.LocationFilter
import com.tripex.pose.domain.location.LocationTracker
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingStateHolder
import com.tripex.pose.domain.usecase.UnlockAreaUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var unlockArea: UnlockAreaUseCase
    @Inject lateinit var trackingState: TrackingStateHolder
    @Inject lateinit var locationFilter: LocationFilter
    @Inject lateinit var logger: Logger
    @Inject @field:DefaultDispatcher lateinit var defaultDispatcher: CoroutineDispatcher

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + defaultDispatcher) }
    private var locationJob: Job? = null
    private var lastAccepted: DomainLocation? = null
    private var sessionUnlocked: Int = 0
    private var lastNotificationAtMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TrackingNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must run before any I/O — Android 14 kills the process if FGS is late.
        ServiceCompat.startForeground(
            this,
            TrackingNotification.NOTIFICATION_ID,
            TrackingNotification.build(
                this,
                getString(R.string.tracking_notification_starting),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                stopTrackingInternal()
                return START_NOT_STICKY
            }
            ACTION_START -> Unit
            else -> Unit
        }

        if (!hasFineLocationPermission()) {
            trackingState.update(TrackingState.PermissionMissing)
            updateNotification(getString(R.string.tracking_notification_permission_missing))
            stopTrackingInternal()
            return START_NOT_STICKY
        }

        startTrackingInternal()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        logger.i(TAG, "Task removed — tracking continues in foreground")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        locationJob?.cancel()
        locationJob = null
        trackingState.update(TrackingState.Idle)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTrackingInternal() {
        if (locationJob?.isActive == true) return

        trackingState.update(TrackingState.Tracking)
        updateNotification(
            getString(R.string.tracking_notification_unlocked, sessionUnlocked),
        )

        locationJob = serviceScope.launch {
            locationTracker.locationUpdates(LocationConfig.DEFAULT)
                .catch { error ->
                    if (error is SecurityException) {
                        trackingState.update(TrackingState.PermissionMissing)
                        updateNotification(
                            getString(R.string.tracking_notification_permission_missing),
                        )
                        stopTrackingInternal()
                    } else {
                        logger.e(TAG, "Location stream failed", error)
                    }
                }
                .collect { location ->
                    val nowNanos = SystemClock.elapsedRealtimeNanos()
                    if (!locationFilter.shouldAccept(
                            candidate = location,
                            lastAccepted = lastAccepted,
                            nowElapsedRealtimeNanos = nowNanos,
                            allowMock = BuildConfig.DEBUG,
                        )
                    ) {
                        return@collect
                    }

                    val newlyUnlocked = unlockArea(location, lastAccepted)
                    lastAccepted = location
                    if (newlyUnlocked > 0) {
                        sessionUnlocked += newlyUnlocked
                        maybeUpdateUnlockedNotification()
                    }
                }
        }
    }

    private fun stopTrackingInternal() {
        locationJob?.cancel()
        locationJob = null
        lastAccepted = null
        trackingState.update(TrackingState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun maybeUpdateUnlockedNotification() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationAtMs < NOTIFICATION_THROTTLE_MS) return
        lastNotificationAtMs = now
        updateNotification(
            getString(R.string.tracking_notification_unlocked, sessionUnlocked),
        )
    }

    private fun updateNotification(text: String) {
        NotificationManagerCompat.from(this).notify(
            TrackingNotification.NOTIFICATION_ID,
            TrackingNotification.build(this, text),
        )
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.tripex.pose.action.START_TRACKING"
        const val ACTION_STOP = "com.tripex.pose.action.STOP_TRACKING"

        private const val TAG = "TrackingService"
        private const val NOTIFICATION_THROTTLE_MS = 10_000L
    }
}
