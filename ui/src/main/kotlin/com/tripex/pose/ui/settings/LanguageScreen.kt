package com.tripex.pose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.ui.components.ChunkyButton
import com.tripex.pose.ui.loading.components.TripailLogo
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme

/**
 * First launch: pick a language, once (V3.5.2).
 *
 * Deliberately the plainest screen in the app. Every word on it would itself need translating
 * before the player has chosen — so there are almost none, and the two options are written in
 * their own languages, which needs no translation at all.
 */
@Composable
fun LanguageRoute(
    onPicked: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    LanguageScreen(
        onPick = { language ->
            viewModel.select(language)
            onPicked()
        },
    )
}

@Composable
private fun LanguageScreen(onPick: (AppLanguage) -> Unit) {
    val cartoon = LocalCartoonStyle.current

    Surface(modifier = Modifier.fillMaxSize(), color = cartoon.paperBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TripailLogo(modifier = Modifier.padding(bottom = 48.dp))

            // Written in both languages, because whoever reads it has not chosen one yet.
            Text(
                text = "Wybierz język  ·  Choose your language",
                style = MaterialTheme.typography.titleMedium,
                color = cartoon.inkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            ChunkyButton(
                text = "Polski",
                onClick = { onPick(AppLanguage.Polish) },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(modifier = Modifier.padding(top = 16.dp)) {
                ChunkyButton(
                    text = "English",
                    onClick = { onPick(AppLanguage.English) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageScreenPreview() {
    TripailTheme { LanguageScreen(onPick = {}) }
}
