package com.tripex.pose.ui.map

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import com.tripex.pose.ui.map.host.MapHostState
import com.tripex.pose.ui.map.host.MapLevel
import com.tripex.pose.ui.map.host.MapScene
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.ui.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MapRoute(
    host: MapHostState,
    onOpenCountry: (iso2: String, bounds: GeoBounds, continentId: String) -> Unit =
        { _, _, _ -> },
    onBack: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoon = stringResource(R.string.settings_coming_soon)

    BackHandler(onBack = onBack)

    // Boundaries step back and the parchment becomes the subject. The camera is deliberately
    // left alone: the country level already framed it, and from there on zooming is the player's
    // business (vision, level 4).
    LaunchedEffect(Unit) {
        host.show(MapScene(level = MapLevel.Explore))
        // Exploring: boundaries are context, not targets — nothing to pick here.
        host.clearTapHandler()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onIntent(
            MapContract.Intent.PermissionsUpdated(
                fineGranted = fine,
                coarseOnly = !fine && coarse,
                backgroundGranted = context.hasBackgroundLocation(),
            ),
        )
    }

    /**
     * Background location is asked for on its own, after fine location (V3.7.4). Android 11+ no
     * longer offers "Allow all the time" in the runtime dialog at all, and even on 10 the request
     * is refused outright if it arrives bundled with the foreground one. A denial here therefore
     * falls through to the app's settings page, which is the only place the choice still lives.
     */
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* best-effort */ }

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onIntent(
            MapContract.Intent.PermissionsUpdated(
                fineGranted = fineGranted,
                coarseOnly = !fineGranted && coarseGranted,
                backgroundGranted = context.hasBackgroundLocation(),
            ),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MapContract.Effect.OpenCountry ->
                    onOpenCountry(effect.iso2, effect.bounds, effect.continentId)
                MapContract.Effect.ShowComingSoon -> snackbarHostState.showSnackbar(comingSoon)
                MapContract.Effect.RequestLocationPermissions -> {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
                MapContract.Effect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                MapContract.Effect.RequestBackgroundLocation -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundPermissionLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        )
                    }
                }
                MapContract.Effect.OpenBatteryOptimizationSettings ->
                    context.openBatteryOptimizationSettings()
                MapContract.Effect.OpenAutostartSettings -> context.openAutostartSettings()
                MapContract.Effect.OpenAppSettings -> {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }
            }
        }
    }

    MapScreen(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
    )
}

/** Below Android 10 while-in-use *is* background, so there is nothing to grant. */
private fun Context.hasBackgroundLocation(): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        true
    } else {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Sends the player to the battery-optimisation list.
 *
 * Deliberately *not* `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which pops a one-tap dialog:
 * that intent is the one Play flags hardest, and some OEM ROMs refuse it outright. The settings
 * list is one tap further away and always resolves.
 */
private fun Context.openBatteryOptimizationSettings() {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
    )
    startFirstResolvable(intents)
}

/**
 * The OEM autostart lists, which is where Xiaomi, Huawei and friends actually decide whether a
 * service may live. None of these are public API, so every one of them is a guess that may not
 * resolve — hence the plain app-settings fallback at the end.
 */
private fun Context.openAutostartSettings() {
    val candidates = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
    ).map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) }

    startFirstResolvable(
        candidates + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
    )
}

private fun Context.startFirstResolvable(intents: List<Intent>) {
    for (intent in intents) {
        val launched = runCatching {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (launched) return
    }
}
