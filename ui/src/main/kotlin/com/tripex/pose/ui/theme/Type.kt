package com.tripex.pose.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tripex.pose.ui.R

private val Fredoka = FontFamily(
    Font(R.font.fredoka_variable, FontWeight.SemiBold),
    Font(R.font.fredoka_variable, FontWeight.Bold),
)

internal val TripexTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = Fredoka,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-2).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = Fredoka,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = Fredoka,
            fontWeight = FontWeight.SemiBold,
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = Fredoka,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}
