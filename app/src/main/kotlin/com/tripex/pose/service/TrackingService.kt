package com.tripex.pose.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tripex.pose.BuildConfig
import com.tripex.pose.R
import com.tripex.pose.core.di.DefaultDispatcher
import com.tripex.pose.core.logging.Logger
import com.tripex.pose.domain.location.DomainLocation
import com.tripex.pose.domain.location.DwellDetector
import com.tripex.pose.domain.location.LocationConfig
import com.tripex.pose.domain.location.LocationFilter
import com.tripex.pose.domain.location.LocationTracker
import com.tripex.pose.domain.location.TrackingIntentRepository
import com.tripex.pose.domain.location.TrackingSession
import com.tripex.pose.domain.location.TrackingSessionRepository
import com.tripex.pose.domain.location.TrackingState
import com.tripex.pose.domain.location.TrackingStateHolder
import com.tripex.pose.domain.usecase.AutoUnlockCityUseCase
import com.tripex.pose.domain.usecase.UnlockAreaUseCase
import com.tripex.pose.settings.AppLanguageApplier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The location Foreground Service (ETAP 7).
 *
 * What makes it survive the app being swiped out of the recents list is not one trick but four,
 * and each one covers a different failure:
 *
 * 1. `stopWithTask="false"` + `START_STICKY` — the service is not tied to the task, and Android
 *    revives it if the process is killed anyway.
 * 2. A **persisted intent** ([TrackingIntentRepository]) — on a revival there is no Intent and no
 *    UI left to ask, so the answer has to already be on disk. Without it the service either
 *    resumes for somebody who switched tracking off, or stops for somebody who did not.
 * 3. A **persisted session** ([TrackingSessionRepository]) — the trail is bridged across the
 *    restart and the dwell timer picks up where it left off instead of at zero.
 * 4. A **partial wake lock** — with the screen off and the CPU asleep, a subscribed collector
 *    still stops collecting.
 *
 * The one thing it cannot do is out-argue an OEM battery manager. That is what the
 * battery-optimisation exemption in the UI is for, and why this class reports GPS silence rather
 * than assuming a live subscription means live fixes.
 */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var unlockArea: UnlockAreaUseCase
    @Inject lateinit var trackingState: TrackingStateHolder
    @Inject lateinit var locationFilter: LocationFilter
    @Inject lateinit var dwellDetector: DwellDetector
    @Inject lateinit var autoUnlockCity: AutoUnlockCityUseCase
    @Inject lateinit var trackingIntent: TrackingIntentRepository
    @Inject lateinit var trackingSession: TrackingSessionRepository
    @Inject lateinit var logger: Logger
    @Inject lateinit var appLanguage: AppLanguageApplier
    @Inject @field:DefaultDispatcher lateinit var defaultDispatcher: CoroutineDispatcher

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + defaultDispatcher) }
    private var locationJob: Job? = null
    private var watchdogJob: Job? = null

    /** Only for the jitter filter; bridging uses [session], which survives a restart. */
    private var lastAccepted: DomainLocation? = null
    private var session: TrackingSession = TrackingSession.EMPTY
    private var lastFixAtMs: Long = 0L
    private var lastNotificationAtMs: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * The context the notification resolves its strings against.
     *
     * A service has no Activity, so below API 33 nothing has installed the chosen language on
     * this process and `getString` would answer in the default one — a Polish notification over
     * an English app (V3.5.6). Read fresh each time so changing the language in settings is
     * picked up by the next update rather than at the next service restart.
     */
    private val strings: Context get() = appLanguage.localize(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TrackingNotification.ensureChannel(strings)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must run before any I/O — Android 14 kills the process if FGS is late.
        ServiceCompat.startForeground(
            this,
            TrackingNotification.NOTIFICATION_ID,
            TrackingNotification.build(
                strings,
                strings.getString(R.string.tracking_notification_starting),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        when (intent?.action) {
            ACTION_STOP -> {
                // The only path that counts as "the player stopped". Everything else —
                // swipe, process death, reboot — is an interruption, not a decision.
                //
                // The stop happens *inside* the coroutine, after the write: stopSelf() leads to
                // onDestroy(), which cancels serviceScope. Stopping first would cancel the very
                // write that makes the stop stick, and the service would come back.
                serviceScope.launch {
                    trackingIntent.set(false)
                    trackingSession.clear()
                    stopTrackingInternal()
                }
                return START_NOT_STICKY
            }

            ACTION_START -> return startIfPermitted()

            // Revived by the system with no Intent: ask the disk what the player wanted.
            else -> {
                logger.i(TAG, "Revived without an Intent — restoring intent from disk")
                serviceScope.launch {
                    if (trackingIntent.isRequested()) {
                        startIfPermitted()
                    } else {
                        logger.i(TAG, "Tracking was switched off — not resuming")
                        stopTrackingInternal()
                    }
                }
                return START_STICKY
            }
        }
    }

    private fun startIfPermitted(): Int {
        if (!hasFineLocationPermission()) {
            trackingState.update(TrackingState.PermissionMissing)
            updateNotification(strings.getString(R.string.tracking_notification_permission_missing))
            stopTrackingInternal()
            return START_NOT_STICKY
        }
        serviceScope.launch { trackingIntent.set(true) }
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
        watchdogJob?.cancel()
        watchdogJob = null
        releaseWakeLock()
        trackingState.update(TrackingState.Idle)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTrackingInternal() {
        if (locationJob?.isActive == true) return

        trackingState.update(TrackingState.Tracking)
        acquireWakeLock()
        updateNotification(strings.getString(R.string.tracking_notification_active))

        locationJob = serviceScope.launch {
            // Restored *before* the first fix arrives, so the very first fix after a restart can
            // already bridge back to where the trail left off (V3.7.3).
            session = trackingSession.load()
            session.lastFix?.let {
                logger.i(TAG, "Restored trail anchor, age=${System.currentTimeMillis() - it.atMs} ms")
            }

            if (!hasBackgroundLocationPermission()) {
                // The quiet failure this whole stage exists to prevent: alive, subscribed,
                // and receiving nothing once the UI is gone.
                logger.w(TAG, "No background location permission — fixes will stop with the UI")
            }

            locationTracker.locationUpdates(LocationConfig.DEFAULT)
                .catch { error ->
                    if (error is SecurityException) {
                        trackingState.update(TrackingState.PermissionMissing)
                        updateNotification(
                            strings.getString(R.string.tracking_notification_permission_missing),
                        )
                        stopTrackingInternal()
                    } else {
                        logger.e(TAG, "Location stream failed", error)
                    }
                }
                .collect { location -> onFix(location) }
        }

        startSilenceWatchdog()
    }

    private suspend fun onFix(location: DomainLocation) {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        // Before the filter on purpose: standing still rejects every fix on jitter, and a
        // watchdog fed only accepted fixes would call that a dead GPS and resubscribe forever.
        lastFixAtMs = SystemClock.elapsedRealtime()

        if (!locationFilter.shouldAccept(
                candidate = location,
                lastAccepted = lastAccepted,
                nowElapsedRealtimeNanos = nowNanos,
                allowMock = BuildConfig.DEBUG,
            )
        ) {
            return
        }

        val nowMs = System.currentTimeMillis()
        val newlyUnlocked = unlockArea(location, session.bridgeableFix(nowMs))
        lastAccepted = location

        val dwell = updateDwell(location, nowMs)
        session = TrackingSession(
            lastFix = TrackingSession.Fix(location.latitude, location.longitude, nowMs),
            dwell = dwell,
        )
        trackingSession.save(session)

        if (newlyUnlocked > 0) maybeUpdateUnlockedNotification()
    }

    /**
     * Standing still in a town claims the whole town.
     *
     * Everything here runs on [defaultDispatcher] inside the service scope — the main thread
     * never sees a fix, a reverse geocode or a database write.
     */
    private suspend fun updateDwell(
        location: DomainLocation,
        nowMs: Long,
    ): DwellDetector.State {
        val (next, dwelled) = dwellDetector.update(
            state = session.dwell,
            lat = location.latitude,
            lng = location.longitude,
            nowMs = nowMs,
        )
        if (!dwelled) return next

        when (val result = autoUnlockCity(location.latitude, location.longitude)) {
            is AutoUnlockCityUseCase.Result.Unlocked ->
                logger.i(TAG, "Auto-unlocked ${result.name} (r=${result.radiusMeters.toInt()} m)")
            AutoUnlockCityUseCase.Result.AlreadyOwned -> Unit
            AutoUnlockCityUseCase.Result.NothingHere -> Unit
        }
        return next
    }

    /**
     * A subscription that stopped delivering looks exactly like standing indoors — until it has
     * looked that way for too long. Saying so in the notification is the difference between
     * discovering the problem now and discovering it after the trip.
     */
    private fun startSilenceWatchdog() {
        watchdogJob?.cancel()
        lastFixAtMs = SystemClock.elapsedRealtime()
        watchdogJob = serviceScope.launch {
            var reported = false
            while (isActive) {
                delay(WATCHDOG_TICK_MS)
                val silentFor = SystemClock.elapsedRealtime() - lastFixAtMs
                if (silentFor >= LocationConfig.GPS_SILENCE_TIMEOUT_MS) {
                    // Once per silent episode. Re-subscribing every tick would be a storm, and
                    // a provider that is genuinely down does not recover faster for being asked
                    // twice a minute.
                    if (!reported) {
                        logger.w(TAG, "No fix for $silentFor ms — resubscribing")
                        updateNotification(strings.getString(R.string.tracking_notification_no_signal))
                        reported = true
                        restartLocationStream()
                    }
                } else if (reported) {
                    reported = false
                    updateNotification(strings.getString(R.string.tracking_notification_active))
                }
            }
        }
    }

    /** Re-subscribes without touching the wake lock, the session or the watchdog. */
    private fun restartLocationStream() {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            locationTracker.locationUpdates(LocationConfig.DEFAULT)
                .catch { error -> logger.e(TAG, "Location stream failed on resubscribe", error) }
                .collect { location -> onFix(location) }
        }
    }

    private fun stopTrackingInternal() {
        locationJob?.cancel()
        locationJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        lastAccepted = null
        session = TrackingSession.EMPTY
        releaseWakeLock()
        trackingState.update(TrackingState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            // Belt and braces: a lock that outlives its release path drains the battery it was
            // taken to protect. The service releases it explicitly; this is the backstop.
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            .onFailure { logger.w(TAG, "Wake lock release failed: ${it.message}") }
        wakeLock = null
    }

    private fun maybeUpdateUnlockedNotification() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationAtMs < NOTIFICATION_THROTTLE_MS) return
        lastNotificationAtMs = now
        updateNotification(strings.getString(R.string.tracking_notification_active))
    }

    /**
     * Updates the ongoing notification's text.
     *
     * Guarded because from Android 13 posting one needs `POST_NOTIFICATIONS`, and the player can
     * refuse it. Refusing does not stop the tracking — the foreground service runs either way —
     * it only means there is nothing to update, so this returns instead of throwing.
     *
     * The suppression is for the guard being in [canPostNotifications] rather than inline, which
     * lint cannot follow across a function boundary. The check is real, and the call is wrapped
     * as well, so a revoked permission mid-flight cannot bring the service down either.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun updateNotification(text: String) {
        if (!canPostNotifications()) return
        runCatching {
            NotificationManagerCompat.from(this).notify(
                TrackingNotification.NOTIFICATION_ID,
                TrackingNotification.build(strings, text),
            )
        }.onFailure { logger.w(TAG, "Could not update the notification: ${it.message}") }
    }

    private fun canPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Below Android 10 there is no such permission — while-in-use *is* background. */
    private fun hasBackgroundLocationPermission(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }

    companion object {
        const val ACTION_START = "com.tripex.pose.action.START_TRACKING"
        const val ACTION_STOP = "com.tripex.pose.action.STOP_TRACKING"

        private const val TAG = "TrackingService"
        private const val NOTIFICATION_THROTTLE_MS = 10_000L
        private const val WATCHDOG_TICK_MS = 30_000L
        private const val WAKE_LOCK_TAG = "tripail:tracking"

        /** Longer than any single trip; the explicit release is the real mechanism. */
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60 * 60 * 1000
    }
}
