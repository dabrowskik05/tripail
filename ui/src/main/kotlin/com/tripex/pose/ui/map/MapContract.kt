package com.tripex.pose.ui.map

import com.tripex.pose.domain.location.TrackingState

/**
 * The explore level: tracking, permissions and the two one-off background-tracking prompts.
 *
 * Search used to live here too. It now belongs to `SearchViewModel`, above the navigation graph,
 * because the magnifier is in the top bar on every level and a search result behaves the same
 * wherever it was typed (V3.2.2).
 */
object MapContract {

    sealed interface StatusMessage {
        data object PreciseLocationRequired : StatusMessage
        data object PermissionMissing : StatusMessage
        data object LocationDisabled : StatusMessage
        data object TrackingActive : StatusMessage
    }

    /**
     * The two one-off screens that stand between "tracking is on" and "tracking survives the
     * phone going in a pocket" (V3.7.4 / V3.7.5).
     */
    enum class BackgroundPrompt {
        /** Location is granted while-in-use only; background needs a second, separate ask. */
        LocationAlways,

        /** Permission is fine — now the battery manager is the thing that kills tracking. */
        Reliability,
    }

    data class State(
        val trackingState: TrackingState = TrackingState.Idle,
        val hasFineLocation: Boolean = false,
        val needsPreciseLocationHint: Boolean = false,
        val statusMessage: StatusMessage? = null,
        val backgroundPrompt: BackgroundPrompt? = null,
    )

    sealed interface Intent {
        /**
         * Discovery has no on/off switch: granting location starts the eraser, and the only way
         * to stop it is to revoke the permission or stop it from the notification.
         */
        data class PermissionsUpdated(
            val fineGranted: Boolean,
            val coarseOnly: Boolean,
            /** Below Android 10 there is no such permission, so callers report `true`. */
            val backgroundGranted: Boolean = true,
        ) : Intent

        data object OpenSettingsRequested : Intent

        /** The player accepted whatever [BackgroundPrompt] is currently on screen. */
        data object BackgroundPromptConfirmed : Intent
        data object BackgroundPromptDismissed : Intent

        /** Secondary action on the reliability screen: the OEM's own autostart list. */
        data object OpenAutostartSettings : Intent
    }

    sealed interface Effect {
        /** Asked separately, and only after fine location was granted (V3.7.4). */
        data object RequestBackgroundLocation : Effect
        data object OpenBatteryOptimizationSettings : Effect
        data object OpenAutostartSettings : Effect
        data object RequestLocationPermissions : Effect
        data object RequestNotificationPermission : Effect
        data object OpenAppSettings : Effect
    }
}
