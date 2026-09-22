package com.tripex.pose.ui.shell.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripex.pose.ui.R
import com.tripex.pose.ui.theme.LocalCartoonStyle
import com.tripex.pose.ui.theme.TripailTheme

private val BarShape = CircleShape
private val ProgressSize = 20.dp

/**
 * The one top bar, identical on every level of the map (V3.2.1).
 *
 * Left: back, then the name of the scope you are in. Right: search, settings, community. The
 * layout does not change between the world, a continent, a country or a region — a control that
 * moves between screens is a control the player has to find again each time.
 *
 * Search is a **mode** of this bar rather than a permanent field. It used to occupy the whole bar
 * on the map screen and did not exist anywhere else; now the magnifier opens it on any level and
 * its own arrow closes it without leaving the level.
 */
@Composable
internal fun TripailTopBar(
    title: String,
    isSearchOpen: Boolean,
    query: String,
    isSearching: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSettingsClick: () -> Unit,
    onCommunityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Always first, always the same shape: back is where the thumb already expects it,
        // whether it leaves search or leaves the level.
        BarAction(
            icon = Icons.Filled.ArrowBack,
            description = stringResource(
                if (isSearchOpen) R.string.search_close_cd else R.string.area_back_cd,
            ),
            onClick = onBack,
        )

        if (isSearchOpen) {
            SearchField(
                query = query,
                isSearching = isSearching,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
        } else {
            if (title.isNotBlank()) {
                Surface(shape = BarShape, color = cartoon.paperBg, shadowElevation = 4.dp) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = cartoon.inkPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            BarAction(
                icon = Icons.Filled.Search,
                description = stringResource(R.string.search_open_cd),
                onClick = onOpenSearch,
            )
            BarAction(
                icon = Icons.Filled.Settings,
                description = stringResource(R.string.map_settings_cd),
                onClick = onSettingsClick,
            )
            BarAction(
                icon = Icons.Filled.Person,
                description = stringResource(R.string.map_community_cd),
                onClick = onCommunityClick,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cartoon = LocalCartoonStyle.current
    val focusRequester = remember { FocusRequester() }

    // Opening search without the keyboard would mean a second tap to do the thing the first tap
    // already asked for.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Surface(modifier = modifier, shape = BarShape, color = cartoon.paperBg, shadowElevation = 6.dp) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ProgressSize),
                        color = cartoon.accentPink,
                        strokeWidth = 2.dp,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val cartoon = LocalCartoonStyle.current
    Surface(shape = CircleShape, color = cartoon.paperBg, shadowElevation = 6.dp) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = description, tint = cartoon.inkPrimary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TripailTopBarBrowsePreview() {
    TripailTheme {
        TripailTopBar(
            title = "Polska",
            isSearchOpen = false,
            query = "",
            isSearching = false,
            onBack = {},
            onOpenSearch = {},
            onQueryChange = {},
            onSubmit = {},
            onSettingsClick = {},
            onCommunityClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
