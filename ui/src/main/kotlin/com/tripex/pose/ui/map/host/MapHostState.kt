package com.tripex.pose.ui.map.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tripex.pose.domain.geo.GeoBounds

/**
 * Bridge between the navigation graph and the single MapLibre instance.
 *
 * Screens describe what they want shown; the surface obeys. Because the surface lives *above*
 * the `NavHost`, moving between levels never destroys or recreates the map — the camera simply
 * stays where it was unless someone explicitly asks it to move.
 */
@Stable
class MapHostState {

    var scene: MapScene by mutableStateOf(MapScene())
        private set

    /** Parchment geometry — world polygon with every unlocked area punched out. */
    var fogGeoJson: String by mutableStateOf("")
        internal set

    var cameraRequest: CameraRequest? by mutableStateOf(null)
        private set

    /**
     * The screen currently in front. Taps are delivered here rather than through a callback wired
     * at the app level, because only the active destination knows what a tap means.
     */
    var onTap: ((com.tripex.pose.ui.explore.BoundaryTap) -> Unit)? by mutableStateOf(null)
        private set

    private var cameraToken = 0L

    fun setTapHandler(handler: (com.tripex.pose.ui.explore.BoundaryTap) -> Unit) {
        onTap = handler
    }

    /**
     * Releases the handler only if it is still the one that was installed.
     *
     * During a navigation transition both screens are briefly composed, and the outgoing one
     * disposes *after* the incoming one has registered. Clearing unconditionally would wipe the
     * new screen's handler and leave the map silently unclickable.
     */
    /** Used by levels where nothing on the map is selectable. */
    fun clearTapHandler() {
        onTap = null
    }

    fun releaseTapHandler(handler: (com.tripex.pose.ui.explore.BoundaryTap) -> Unit) {
        if (onTap === handler) onTap = null
    }

    fun show(scene: MapScene) {
        this.scene = scene
    }

    /**
     * Asks the camera to frame [bounds]. The only way the camera ever moves on its own — and it
     * is deliberately awkward to reach from a selection handler.
     */
    fun flyTo(bounds: GeoBounds, animate: Boolean = true) {
        cameraToken += 1
        cameraRequest = CameraRequest(bounds = bounds, animate = animate, token = cameraToken)
    }

    fun onCameraApplied(token: Long) {
        if (cameraRequest?.token == token) cameraRequest = null
    }
}
