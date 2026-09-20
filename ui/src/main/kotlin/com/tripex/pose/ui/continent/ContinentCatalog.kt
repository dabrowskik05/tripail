package com.tripex.pose.ui.continent

import androidx.compose.ui.graphics.Color
import com.tripex.pose.domain.geo.ContinentId
import com.tripex.pose.ui.R

/**
 * Presentation catalog for the cartoon world overview. Paths are stylized (not GIS-accurate).
 * ViewBox: 0 0 1000 520.
 */
internal data class ContinentCatalogEntry(
    val id: ContinentId,
    val nameRes: Int,
    val pathData: String,
    val color: Color,
    val bounceDelayMillis: Int,
)

internal object ContinentCatalog {
    const val VIEW_BOX_WIDTH = 1000f
    const val VIEW_BOX_HEIGHT = 520f

    val entries: List<ContinentCatalogEntry> = listOf(
        ContinentCatalogEntry(
            id = ContinentId.NorthAmerica,
            nameRes = R.string.continent_north_america,
            pathData = "M140,90 L220,70 L280,95 L300,150 L270,210 L230,250 L180,240 L150,200 L120,160 Z",
            color = Color(0xFF9AA5B1),
            bounceDelayMillis = 0,
        ),
        ContinentCatalogEntry(
            id = ContinentId.SouthAmerica,
            nameRes = R.string.continent_south_america,
            pathData = "M250,280 L290,270 L310,320 L300,400 L270,450 L240,420 L230,340 Z",
            color = Color(0xFF4CAF6D),
            bounceDelayMillis = 400,
        ),
        ContinentCatalogEntry(
            id = ContinentId.Europe,
            nameRes = R.string.continent_europe,
            pathData = "M480,90 L540,80 L570,110 L560,160 L520,170 L490,140 Z",
            color = Color(0xFF4C8FE0),
            bounceDelayMillis = 800,
        ),
        ContinentCatalogEntry(
            id = ContinentId.Africa,
            nameRes = R.string.continent_africa,
            pathData = "M500,180 L560,170 L590,230 L580,320 L540,360 L500,330 L490,250 Z",
            color = Color(0xFFF5893C),
            bounceDelayMillis = 1200,
        ),
        ContinentCatalogEntry(
            id = ContinentId.Asia,
            nameRes = R.string.continent_asia,
            pathData = "M580,70 L720,60 L800,100 L820,180 L780,220 L700,210 L640,170 L590,130 Z",
            color = Color(0xFFD64545),
            bounceDelayMillis = 1600,
        ),
        ContinentCatalogEntry(
            id = ContinentId.Oceania,
            nameRes = R.string.continent_oceania,
            pathData = "M780,300 L860,290 L900,330 L880,380 L820,370 L790,340 Z",
            color = Color(0xFFB4552F),
            bounceDelayMillis = 2000,
        ),
        ContinentCatalogEntry(
            id = ContinentId.Antarctica,
            nameRes = R.string.continent_antarctica,
            pathData = "M200,470 L800,470 L780,500 L220,500 Z",
            color = Color(0xFFF1F5F8),
            bounceDelayMillis = 2400,
        ),
    )

    fun entry(id: ContinentId): ContinentCatalogEntry = entries.first { it.id == id }
}
