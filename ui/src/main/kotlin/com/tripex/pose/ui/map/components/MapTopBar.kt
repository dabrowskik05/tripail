package com.tripex.pose.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * The map's top bar: search on the left, actions on the right.
 *
 * Settings used to hide behind a floating action button in the opposite corner, which is the last
 * place anyone looks for them. Search and settings are the two things a player reaches for, so
 * they live together, always visible, at the top.
 */
@Composable
internal fun MapTopBar(
    query: String,
    isSearching: Boolean,
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
        Surface(
            modifier = Modifier.weight(1f),
            shape = BarShape,
            color = cartoon.paperBg,
            shadowElevation = 6.dp,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = cartoon.inkPrimary,
                    )
                },
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
                modifier = Modifier.fillMaxWidth(),
            )
        }

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

@Composable
private fun BarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun MapTopBarPreview() {
    TripailTheme {
        MapTopBar(
            query = "Warszawa",
            isSearching = false,
            onQueryChange = {},
            onSubmit = {},
            onSettingsClick = {},
            onCommunityClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
