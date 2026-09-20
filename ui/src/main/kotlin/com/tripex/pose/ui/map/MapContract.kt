package com.tripex.pose.ui.map

import com.tripex.pose.domain.geo.FogGeoJsonBuilder
import com.tripex.pose.domain.geo.MapViewport
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
        val typeLabel: String,
        val unlockedHexCount: Int,
        val expanded: Boolean = false,
    )

    data class State(
        val styleUri: String = "",
        val fogGeoJson: String = FogGeoJsonBuilder().emptyWorld(),
        val initialViewport: MapViewport = MapViewport.DEFAULT,
        val trackingState: TrackingState = TrackingState.Idle,
        val unlockedCount: Int = 0,
        val hasFineLocation: Boolean = false,
        val needsPreciseLocationHint: Boolean = false,
        val statusMessage: StatusMessage? = null,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val searchMessage: SearchMessage? = null,
        val cameraTarget: CameraTarget? = null,
        val fabExpanded: Boolean = false,
        val settingsVisible: Boolean = false,
        val communityVisible: Boolean = false,
        val placeDetail: PlaceDetail? = null,
    )

    sealed interface Intent {
        data object StartDiscovery : Intent
        data object StopDiscovery : Intent
        data class PermissionsUpdated(
            val fineGranted: Boolean,
            val coarseOnly: Boolean,
            val startIfGranted: Boolean = false,
        ) : Intent
        data class CameraIdle(val viewport: MapViewport) : Intent
        data class SearchQueryChanged(val query: String) : Intent
        data object SubmitSearch : Intent
        data object CameraTargetConsumed : Intent
        data object OpenSettingsRequested : Intent
        data object ZoomIn : Intent
        data object ZoomOut : Intent
        data object ToggleFabMenu : Intent
        data object OpenSettings : Intent
        data object CloseSettings : Intent
        data object OpenCommunity : Intent
        data object CloseCommunity : Intent
        data object DismissPlaceDetail : Intent
        data object TogglePlaceDetailExpanded : Intent
        data class FocusCamera(val target: CameraTarget) : Intent
    }

    sealed interface Effect {
        data object RequestLocationPermissions : Effect
        data object RequestNotificationPermission : Effect
        data object OpenAppSettings : Effect
    }
}
