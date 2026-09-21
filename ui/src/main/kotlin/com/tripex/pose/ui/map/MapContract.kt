package com.tripex.pose.ui.map

import com.tripex.pose.domain.geo.GeoBounds
import com.tripex.pose.domain.geo.MapViewport
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.geo.PlaceKind
import com.tripex.pose.domain.location.TrackingState

object MapContract {
    data class CameraTarget(
        val latitude: Double,
        val longitude: Double,
        val zoom: Double = MapViewport.DEFAULT.zoom,
    )

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

    sealed interface SearchMessage {
        data class Unlocked(val count: Int, val placeName: String) : SearchMessage
        data class Failed(val detail: String?) : SearchMessage
    }

    /**
     * Detail for a place unlocked via search.
     * No fake coverage % — only the real hex count from UnlockPlaceUseCase.
     */
    data class PlaceDetail(
        val name: String,
        /** Resolved to a Polish label in the sheet — enum names must not reach the screen. */
        val kind: PlaceKind,
        val expanded: Boolean = false,
    )

    data class State(
        val trackingState: TrackingState = TrackingState.Idle,
        val hasFineLocation: Boolean = false,
        val needsPreciseLocationHint: Boolean = false,
        val statusMessage: StatusMessage? = null,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val searchMessage: SearchMessage? = null,
        val suggestions: List<Place> = emptyList(),
        val cameraTarget: CameraTarget? = null,
        val communityVisible: Boolean = false,
        val placeDetail: PlaceDetail? = null,
        val backgroundPrompt: BackgroundPrompt? = null,
    )

    sealed interface Intent {
        /**
         * Discovery has no on/off switch any more: granting location starts the eraser, and the
         * only way to stop it is to revoke the permission or leave the app.
         */
        data class PermissionsUpdated(
            val fineGranted: Boolean,
            val coarseOnly: Boolean,
            /**
             * Below Android 10 there is no such permission, so callers report `true` — see
             * `MapRoute`. Defaulted so a caller that does not care about background tracking
             * (and every existing test) keeps compiling.
             */
            val backgroundGranted: Boolean = true,
        ) : Intent
        data class CameraIdle(val viewport: MapViewport) : Intent
        data class SearchQueryChanged(val query: String) : Intent
        data object SubmitSearch : Intent

        /** A suggestion was picked — unlock that place. */
        data class SuggestionPicked(val place: Place) : Intent
        data object DismissSuggestions : Intent
        data object CameraTargetConsumed : Intent
        data object OpenSettingsRequested : Intent
        /** Settings are not built yet; this only raises [Effect.ShowComingSoon]. */
        data object OpenSettings : Intent
        data object OpenCommunity : Intent
        data object CloseCommunity : Intent
        data object DismissPlaceDetail : Intent

        /** The player accepted whatever [BackgroundPrompt] is currently on screen. */
        data object BackgroundPromptConfirmed : Intent
        data object BackgroundPromptDismissed : Intent

        /** Secondary action on the reliability screen: the OEM's own autostart list. */
        data object OpenAutostartSettings : Intent
        data object TogglePlaceDetailExpanded : Intent
        data class FocusCamera(val target: CameraTarget) : Intent
    }

    sealed interface Effect {
        /** A searched country is a level to enter, not an area to own (M4.10). */
        data class OpenCountry(
            val iso2: String,
            val bounds: GeoBounds,
            val label: String,
            val continentId: String,
        ) : Effect
        data object ShowComingSoon : Effect

        /** Asked separately, and only after fine location was granted (V3.7.4). */
        data object RequestBackgroundLocation : Effect
        data object OpenBatteryOptimizationSettings : Effect
        data object OpenAutostartSettings : Effect
        data object RequestLocationPermissions : Effect
        data object RequestNotificationPermission : Effect
        data object OpenAppSettings : Effect
    }
}
