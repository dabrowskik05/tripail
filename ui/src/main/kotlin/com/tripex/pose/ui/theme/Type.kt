package com.tripex.pose.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tripex.pose.ui.R

/**
 * Baloo 2, not Fredoka.
 *
 * Fredoka — including the upstream Google Fonts build — ships 320 glyphs and covers neither
 * `ą ć ę ń ś ź ż` nor their capitals, so every Polish string fell back to the system font
 * mid-word. Baloo 2 keeps the rounded, chunky character and carries Latin Extended-A.
 *
 * The file is a variable font with a single `wght` axis (400–800); the weights below are real
 * instances, not synthetic emboldening.
 */
private fun balooWeight(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

private val Baloo = FontFamily(
    Font(R.font.baloo2_variable, FontWeight.Medium, variationSettings = balooWeight(500)),
    Font(R.font.baloo2_variable, FontWeight.SemiBold, variationSettings = balooWeight(600)),
    Font(R.font.baloo2_variable, FontWeight.Bold, variationSettings = balooWeight(700)),
)

internal val TripexTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = Baloo,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = Baloo,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = Baloo,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = base.bodyLarge.copy(fontFamily = Baloo),
        bodyMedium = base.bodyMedium.copy(fontFamily = Baloo),
        bodySmall = base.bodySmall.copy(fontFamily = Baloo),
        labelLarge = base.labelLarge.copy(
            fontFamily = Baloo,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}
