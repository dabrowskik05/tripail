package com.tripex.pose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripex.pose.domain.settings.AppLanguage
import com.tripex.pose.ui.R
import com.tripex.pose.ui.shell.chrome.AppChromeState
import com.tripex.pose.ui.shell.chrome.RegisterChrome
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme

/**
 * Settings, with one real entry (V3.5.3).
 *
 * The cog used to raise a "coming soon" snackbar. It now opens this, because there is finally
 * something to configure — and changing it takes effect immediately, without a restart and
 * without losing your place in the map.
 */
@Composable
fun SettingsRoute(
    chrome: AppChromeState,
    onBack: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()

    RegisterChrome(chrome = chrome, title = stringResource(R.string.settings_title), onBack = onBack)

    SettingsScreen(selected = language, onSelect = viewModel::select)
}

@Composable
private fun SettingsScreen(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    val cartoon = LocalCartoonStyle.current

    Surface(modifier = Modifier.fillMaxSize(), color = cartoon.paperBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .padding(top = CHROME_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = cartoon.inkPrimary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            for (language in AppLanguage.entries) {
                LanguageRow(
                    language = language,
                    isSelected = language == selected,
                    onSelect = { onSelect(language) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val cartoon = LocalCartoonStyle.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cartoon.paperBg,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = isSelected, onClick = onSelect)
                // Each language is named in itself: "Polski", not "Polish".
                Text(
                    text = when (language) {
                        AppLanguage.Polish -> "Polski"
                        AppLanguage.English -> "English"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = cartoon.inkPrimary,
                )
            }
        }
    }
}

/** Room for the shared top bar, which the shell draws above this screen. */
private val CHROME_CLEARANCE = 64.dp

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TripailTheme { SettingsScreen(selected = AppLanguage.Polish, onSelect = {}) }
}
