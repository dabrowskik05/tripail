package com.tripex.pose.ui.continent

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.ui.R

/**
 * Presentation-only mapping from [ContinentId] to fill colour and label.
 * Replaces the hand-drawn `ContinentCatalog` — shapes now come from the atlas (M2.5).
 *
 * Outlines and dimming are derived from each continent's own fill rather than from a shared ink
 * colour, so the map reads as one flat, modern palette instead of everything being edged in grey.
 */
internal object ContinentPalette {

    /** How much darker an outline is than its fill. */
    private const val OUTLINE_DARKEN = 0.22f

    /** How much darker a coastline's inner shadow is than its fill. */
    private const val COAST_SHADOW_DARKEN = 0.3f

    /** Darkest a dimmed (unselected) continent gets at full dim. */
    private const val DIM_DARKEN = 0.42f

    /** Dimmed continents also lose saturation so the selected one keeps the eye. */
    private const val DIM_DESATURATE = 0.35f

    private val fills: Map<ContinentId, Color> = mapOf(
        ContinentId.NorthAmerica to Color(0xFF4A8B80),
        ContinentId.SouthAmerica to Color(0xFF85C25F),
        ContinentId.Africa to Color(0xFFE39B3B),
        ContinentId.Asia to Color(0xFFD95B5B),
        ContinentId.Europe to Color(0xFF8F75AD),
        ContinentId.Oceania to Color(0xFFC76345),
        ContinentId.Antarctica to Color(0xFFF2F6F9),
    )

    private val labels: Map<ContinentId, Int> = mapOf(
        ContinentId.NorthAmerica to R.string.continent_north_america,
        ContinentId.SouthAmerica to R.string.continent_south_america,
        ContinentId.Europe to R.string.continent_europe,
        ContinentId.Africa to R.string.continent_africa,
        ContinentId.Asia to R.string.continent_asia,
        ContinentId.Oceania to R.string.continent_oceania,
        ContinentId.Antarctica to R.string.continent_antarctica,
    )

    private val FallbackFill = Color(0xFF9AA5B1)

    fun fill(id: ContinentId): Color = fills[id] ?: FallbackFill

    /**
     * Surface of the bottom menu card. Antarctica's near-white, so the strip behind the system
     * navigation bar reads as part of the map's own palette rather than as a grey system bar.
     */
    val menuSurface: Color get() = fill(ContinentId.Antarctica)

    @StringRes
    fun label(id: ContinentId): Int = labels.getValue(id)

    /** Inner shadow along the coast, giving the land a slightly raised feel. */
    fun coastShadow(id: ContinentId): Color = fill(id).darken(COAST_SHADOW_DARKEN)

    /**
     * Fill of an unselected continent while another one is picked: a darker, less saturated
     * version of its own colour, so the map stays readable instead of turning into grey mush.
     */
    fun dimmedFill(id: ContinentId, fraction: Float): Color {
        val base = fill(id)
        if (fraction <= 0f) return base
        val target = base.darken(DIM_DARKEN).desaturate(DIM_DESATURATE)
        return lerp(base, target, fraction.coerceIn(0f, 1f))
    }

    /** Border colour: a darker shade of the continent itself, never black or grey. */
    fun dimmedOutline(id: ContinentId, fraction: Float): Color =
        dimmedFill(id, fraction).darken(OUTLINE_DARKEN)


    /**
     * Stage B takes its whole palette from the continent you entered (vision §2): the parchment,
     * the countries and the water around them are all shades of one colour, so a continent is
     * recognisable at a glance without reading a single label.
     */
    fun mapTints(id: ContinentId?): MapTints {
        val base = id?.let { fill(it) } ?: FallbackFill
        return MapTints(
            parchment = base.lighten(PARCHMENT_LIGHTEN),
            land = base.lighten(LAND_LIGHTEN),
            water = base.lighten(WATER_LIGHTEN).desaturate(WATER_DESATURATE),
            border = base.darken(OUTLINE_DARKEN),
            selected = base.lighten(SELECTED_SHIFT),
            restOfCountry = base.darken(SELECTED_SHIFT),
        )
    }

    data class MapTints(
        val parchment: Color,
        val land: Color,
        val water: Color,
        val border: Color,
        /**
         * The picked country or region: the continent's own colour, 20 % lighter (2026-09-24).
         * It replaced one warm yellow for every continent, which fought each continent's palette.
         */
        val selected: Color,
        /** The other regions of the country a picked region belongs to: 20 % darker. */
        val restOfCountry: Color,
    )

    private const val PARCHMENT_LIGHTEN = 0.62f
    private const val LAND_LIGHTEN = 0.35f
    private const val WATER_LIGHTEN = 0.78f
    private const val WATER_DESATURATE = 0.25f
    private const val SELECTED_SHIFT = 0.2f

    private fun Color.lighten(fraction: Float): Color = lerp(this, Color.White, fraction)

    /** Stable per-continent phase so the idle bounce does not move in lockstep. */
    fun bounceDelayMillis(id: ContinentId): Int = id.ordinal * BOUNCE_STEP_MILLIS

    private const val BOUNCE_STEP_MILLIS = 400

    private fun Color.darken(fraction: Float): Color = lerp(this, Color.Black, fraction)

    private fun Color.desaturate(fraction: Float): Color {
        val grey = red * LUMA_R + green * LUMA_G + blue * LUMA_B
        return lerp(this, Color(grey, grey, grey, alpha), fraction)
    }

    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
}
