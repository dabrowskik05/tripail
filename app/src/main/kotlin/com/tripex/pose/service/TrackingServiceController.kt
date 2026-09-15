package com.tripex.pose.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tripex.pose.domain.location.TrackingController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrackingController {

    override fun startTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(intent)
    }
}
