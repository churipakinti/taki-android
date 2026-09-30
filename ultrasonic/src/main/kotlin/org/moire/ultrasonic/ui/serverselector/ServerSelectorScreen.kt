/*
 * ServerSelectorScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.ColorUtils
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests scroll to a row / find the scrollable content. */
const val SERVER_SELECTOR_CONTENT_TEST_TAG = "server_selector_content"
private const val ADD_SERVER_ROW_KEY = "add_server"
private const val LUMINANCE_LIMIT = 0.5

/**
 * Server Selector (post-issue-#10 residual migration, phase 5A2): a simple "your libraries"
 * list - a 1:1 behavioural port of the legacy `ServerSelectorFragment`/`ServerRowAdapter`
 * (`RecyclerView` + FAB). Draws its own [TakiScreenHeader]; the Activity's shared Material toolbar
 * and shared back bar are both hidden/removed for this destination (see `NavigationActivity`).
 *
 * Reorder ("Move up"/"Move down") was **not** carried forward from the legacy row's "⋮" menu: the
 * underlying query has no `ORDER BY`, so the swap only rewrites the `ServerSetting.index` column
 * without changing the on-screen order - its only observable effect was silently desyncing the
 * index-based value `EditServerFragment` reads (see the phase 5A2 audit). Edit/Delete, the two
 * affordances the product direction actually requires, are unaffected by that removal.
 */
@Composable
fun ServerSelectorScreen(
    state: ServerSelectorUiState,
    actions: ServerSelectorActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TakiScreenHeader(
                    onBack = actions.onBack,
                    title = stringResource(R.string.server_selector_label),
                )

                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(SERVER_SELECTOR_CONTENT_TEST_TAG),
                    contentPadding = PaddingValues(
                        horizontal = TakiTheme.spacing.md,
                        vertical = TakiTheme.spacing.sm,
                    ),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        ServerRow(row = row, actions = actions)
                        Spacer(Modifier.height(TakiTheme.spacing.sm))
                    }
                    item(key = ADD_SERVER_ROW_KEY) {
                        AddServerRow(onClick = actions.onAddServer)
                    }
                }
            }

            DeleteServerSheet(
                visible = state.pendingDelete != null,
                serverName = state.pendingDelete?.name.orEmpty(),
                onConfirm = actions.onDeleteConfirm,
                onDismiss = actions.onDeleteCancel,
                bottomContentInset = bottomContentInset,
            )
        }
    }
}

@Composable
private fun ServerRow(row: ServerSelectorRow, actions: ServerSelectorActions) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TakiTheme.shapes.sm)
                .background(TakiTheme.colors.surfaceHigh)
                .clickable(role = Role.Button) { actions.onServerClick(row) }
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerSwatch(row)
            Spacer(Modifier.width(TakiTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = TakiTheme.type.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.description != null) {
                    Text(
                        text = row.description,
                        style = TakiTheme.type.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.isActive) {
                    Text(
                        text = stringResource(R.string.server_selector_active),
                        style = TakiTheme.type.caption,
                        color = TakiTheme.colors.accent,
                    )
                }
            }
            if (!row.isOffline) {
                TakiIconButton(
                    onClick = { menuExpanded = true },
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.server_selector_row_menu, row.name),
                    iconSize = TakiTheme.dimensions.iconSm,
                )
            }
        }
        if (!row.isOffline) {
            ServerRowMenu(
                expanded = menuExpanded,
                row = row,
                onDismiss = { menuExpanded = false },
                onEdit = { menuExpanded = false; actions.onEditServer(row) },
                onDelete = { menuExpanded = false; actions.onDeleteRequested(row) },
            )
        }
    }
}

@Composable
private fun ServerRowMenu(
    expanded: Boolean,
    row: ServerSelectorRow,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editLabel = stringResource(R.string.server_menu_edit)
    val deleteLabel = stringResource(R.string.server_menu_delete)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(editLabel) },
            onClick = onEdit,
            modifier = Modifier.semantics { contentDescription = "$editLabel ${row.name}" },
        )
        DropdownMenuItem(
            text = { Text(deleteLabel) },
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = "$deleteLabel ${row.name}" },
        )
    }
}

/**
 * The round color swatch behind the server/offline glyph - the same idea as the legacy
 * `server_image` treatment (`ServerColor.getBackgroundColor`/`getForegroundColor`: a configured
 * server color, or the app's own accent as a neutral fallback; a luminance-picked light/dark
 * glyph tint), reimplemented with Compose tokens instead of `ServerColor`'s
 * `MaterialColors.getColor`/`harmonizeWithPrimary` calls - those resolve an XML theme attribute
 * (`?attr/colorPrimary`) that only exists on a real themed Activity, which a Compose screen has
 * no reason to depend on (and which is absent in a plain Compose test host, where this failed
 * with `IllegalArgumentException` before this change). The neutral fallback is
 * [TakiTheme.colors.accent] directly - textually the exact color `ServerColor`'s own fallback
 * comment already named as its intent ("the Taki accent") - and the luminance split reuses the
 * same two tokens `R.color.selected_menu_dark`/`_light` alias (`taki_ivory`/`taki_black`).
 */
@Composable
private fun ServerSwatch(row: ServerSelectorRow) {
    val background = row.color?.let { Color(it) } ?: TakiTheme.colors.accent
    val foreground = if (ColorUtils.calculateLuminance(background.toArgb()) < LUMINANCE_LIMIT) {
        TakiTheme.colors.ivory
    } else {
        TakiTheme.colors.black
    }
    Box(
        modifier = Modifier
            .size(TakiTheme.dimensions.artworkMini)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (row.isOffline) R.drawable.ic_menu_screen_on_off else R.drawable.ic_menu_server,
            ),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
    }
}

@Composable
private fun AddServerRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add_white),
            contentDescription = null,
            tint = TakiTheme.colors.gray,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
        Text(
            text = stringResource(R.string.server_editor_new_label),
            style = TakiTheme.type.titleSmall,
            modifier = Modifier.padding(start = TakiTheme.spacing.sm),
        )
    }
}
