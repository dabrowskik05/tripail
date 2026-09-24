package com.tripex.pose.ui.map.host

import androidx.compose.runtime.Composable
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

    /** The request the surface should apply now — the head of [pendingCamera]. */
    var cameraRequest: CameraRequest? by mutableStateOf(null)
        private set

    /**
     * Requests wait their turn instead of replacing each other. Entering a continent places it
     * whole and then flies to the player; with a single slot the second request could overwrite
     * the first before the map had applied it, so sometimes only one of them happened.
     */
    private val pendingCamera = ArrayDeque<CameraRequest>()

    /**
     * True once the style is loaded and the first camera placement applied — the moment the map
     * is worth showing. Screens hold their panels back until then, so text does not arrive over
     * an empty map.
     */
    var isReady: Boolean by mutableStateOf(false)
        private set

    internal fun markReady() {
        isReady = true
    }

    /**
     * The card the current level wants at the bottom of the map — country stats, region stats,
     * the "tap a country" hint.
     *
     * Levels do not draw their own card any more. Each one drew its card inside its own screen,
     * so during a navigation both screens — and both cards — were on screen at once, a region's
     * panel sliding up over the country's. The shell now draws exactly one card from here (or the
     * place sheet instead of it), so every change is the same "old one down, new one up".
     */
    var levelPanel: BottomPanel? by mutableStateOf(null)
        private set
    private var panelOwner: Any? = null

    /** The level that claimed last owns the slot; see [releaseTapHandler] for why that matters. */
    fun claimPanel(owner: Any) {
        panelOwner = owner
    }

    fun updatePanel(owner: Any, panel: BottomPanel?) {
        if (panelOwner === owner) levelPanel = panel
    }

    fun releasePanel(owner: Any) {
        if (panelOwner === owner) {
            panelOwner = null
            levelPanel = null
        }
    }

    /**
     * Box the camera may not leave, or `null` for the whole world (V3.3.7).
     *
     * Set when a continent is being explored, so panning cannot wander off onto neighbouring,
     * still-fogged continents. Cleared whenever there is no continent in context — a camera
     * locked into a box nobody can unlock is worse than no lock at all.
     */
    var maxBounds: GeoBounds? by mutableStateOf(null)
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
     * Country of the city whose sheet is open, or `null`. While set, the map shows that country's
     * regions and drops the highlight of whatever region was picked before — the city is the
     * selection now.
     */
    var placeCountry: String? by mutableStateOf(null)
        private set

    fun focusPlaceCountry(iso2: String?) {
        placeCountry = iso2
    }

    /** What the map actually draws: the level's [scene], or the city's country on top of it. */
    val renderedScene: MapScene
        get() = placeCountry?.let { iso2 ->
            scene.copy(level = MapLevel.Country, countryIso2 = iso2, selectedId = null)
        } ?: scene

    fun limitTo(bounds: GeoBounds?) {
        maxBounds = bounds
    }

    /**
     * Asks the camera to frame [bounds]. The only way the camera ever moves on its own — and it
     * is deliberately awkward to reach from a selection handler.
     */
    /**
     * @param fill true when [bounds] is the subject of the screen rather than context, so the
     *   shape should reach the edges (V3.3.5).
     */
    fun flyTo(bounds: GeoBounds, animate: Boolean = true, fill: Boolean = false) {
        cameraToken += 1
        pendingCamera.addLast(
            CameraRequest(
                bounds = bounds,
                animate = animate,
                token = cameraToken,
                paddingFraction = if (fill) CameraRequest.FILL_PADDING else CameraRequest.FRAME_PADDING,
            ),
        )
        if (cameraRequest == null) cameraRequest = pendingCamera.first()
    }

    fun onCameraApplied(token: Long) {
        if (pendingCamera.firstOrNull()?.token != token) return
        pendingCamera.removeFirst()
        cameraRequest = pendingCamera.firstOrNull()
    }
}

/**
 * One bottom card: [key] says *which* card it is, [content] draws it. A new key slides the old
 * card out and this one in; the same key with new content just updates in place.
 */
class BottomPanel(
    val key: Any,
    val content: @Composable () -> Unit,
)
