/*
 * LibraryHubSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiSheet
import org.moire.ultrasonic.ui.theme.TakiTheme

const val LIBRARY_HUB_SHEET_TEST_TAG = "library_hub_sheet"
const val LIBRARY_HUB_CURRENT_TEST_TAG = "library_hub_current"

fun libraryHubActionTestTag(action: LibraryHubAction) = "library_hub_${action.name.lowercase()}"

/** The four navigations the hub offers, in the legacy popup's order after the current-library row. */
enum class LibraryHubAction { SWITCH, ADD, SETTINGS, ABOUT }

/**
 * The Compose replacement for the AppCompat `PopupMenu` the Home/Library ⋮ used to open (issue
 * #10 phase 5A6). Exactly the legacy entries in the legacy order: a disabled, informational
 * "current library" row showing the active collection's name (exposed to accessibility as a
 * disabled, non-actionable row), then Switch collection, Add collection, Settings, About. A bottom
 * sheet rather than an anchored dropdown: five full-width, 48dp+ rows read better on a phone than
 * a small anchored menu and the old popup's anchor view hack is no longer needed.
 *
 * Choosing an action reports it once ([onAction] cannot fire twice from a double tap); dismissing
 * navigates nowhere.
 */
@Composable
fun LibraryHubSheet(
    currentLibraryName: String,
    onAction: (LibraryHubAction) -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    var consumed by remember { mutableStateOf(false) }
    val currentLabel = stringResource(R.string.library_hub_current)
    TakiSheet(
        title = stringResource(R.string.library_hub_title),
        dismissLabel = stringResource(R.string.common_cancel),
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = LIBRARY_HUB_SHEET_TEST_TAG,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TakiTheme.dimensions.touchTargetMin)
                .testTag(LIBRARY_HUB_CURRENT_TEST_TAG)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$currentLabel: $currentLibraryName"
                    disabled()
                }
                .padding(vertical = TakiTheme.spacing.sm),
        ) {
            Text(text = currentLabel, style = TakiTheme.type.caption, color = TakiTheme.colors.gray)
            Text(
                text = currentLibraryName,
                style = TakiTheme.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HubRow(R.string.library_hub_switch, LibraryHubAction.SWITCH) { action ->
            if (!consumed) {
                consumed = true
                onAction(action)
            }
        }
        HubRow(R.string.library_hub_add, LibraryHubAction.ADD) { action ->
            if (!consumed) {
                consumed = true
                onAction(action)
            }
        }
        HubRow(R.string.menu_settings, LibraryHubAction.SETTINGS) { action ->
            if (!consumed) {
                consumed = true
                onAction(action)
            }
        }
        HubRow(R.string.menu_about, LibraryHubAction.ABOUT) { action ->
            if (!consumed) {
                consumed = true
                onAction(action)
            }
        }
    }
}

@Composable
private fun HubRow(labelRes: Int, action: LibraryHubAction, onClick: (LibraryHubAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TakiTheme.dimensions.touchTargetMin)
            .testTag(libraryHubActionTestTag(action))
            .clickable(role = Role.Button) { onClick(action) }
            .padding(vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(labelRes), style = TakiTheme.type.body)
    }
}
