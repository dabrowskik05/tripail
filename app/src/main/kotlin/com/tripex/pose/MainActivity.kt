package com.tripex.pose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tripex.pose.domain.location.TrackingController
import com.tripex.pose.ui.TripailApp
import com.tripex.pose.ui.theme.TripailTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var trackingController: TrackingController

    private var askedThisLaunch = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestNotificationPermissionIfNeeded()
            trackingController.startTracking()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* best-effort: the service runs either way, the notification is just quieter */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripailTheme {
                TripailApp()
            }
        }
    }

    /**
     * Discovery is not a mode the player switches on (ETAP 5). Location is asked for once, and
     * from then on opening the app is enough — the tracker is a foreground service, so it keeps
     * erasing fog after the app is backgrounded.
     *
     * This lives here rather than on the map screen because a player sitting in the continent
     * menu is still walking around the real world.
     */
    override fun onStart() {
        super.onStart()
        when {
            hasFineLocation() -> trackingController.startTracking()
            // Asking again on every return from the background would be nagging; the map screen
            // still explains the refusal and offers a way back to system settings.
            !askedThisLaunch -> {
                askedThisLaunch = true
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
}
