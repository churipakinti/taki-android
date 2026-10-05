/*
 * AddToPlaylistSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TAKI_SHEET_EMPTY_TEXT_TEST_TAG
import org.moire.ultrasonic.ui.components.TakiPickerOption
import org.moire.ultrasonic.ui.components.TakiPickerSheet
import org.moire.ultrasonic.ui.components.TakiSheet
import org.moire.ultrasonic.ui.theme.TakiTheme

const val ADD_TO_PLAYLIST_SHEET_TEST_TAG = "add_to_playlist_sheet"

/** One playlist the user can add to; [id] is the stable identity, [name] what is displayed. */
data class AddToPlaylistOption(val id: String, val name: String)

fun addToPlaylistOptionTestTag(id: String) =
    org.moire.ultrasonic.ui.components.takiPickerOptionTestTag(id)

/**
 * The Compose replacement for the legacy "Add to playlist" `ItemSelectionDialogFragment` (issue
 * #10 phase 5A6): the user's server playlists, in exactly the order the server returned them,
 * titled "Add to playlist". Selecting one reports its [AddToPlaylistOption.id] once; dismissing
 * (scrim, Back) reports nothing and mutates nothing.
 *
 * Playlists are identified by id, not by display name as the legacy name-matching did: two
 * playlists sharing a name were indistinguishable there (the first always won), here each row adds
 * to the playlist that was tapped. The list is never empty in practice - the host toasts and does
 * not open the sheet when the server has no playlists - but an empty list still renders the same
 * "No saved playlists in this library" message rather than an empty panel.
 */
@Composable
fun AddToPlaylistSheet(
    playlists: List<AddToPlaylistOption>,
    onSelect: (playlistId: String) -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    val title = stringResource(R.string.playlist_add_to_title)
    val dismissLabel = stringResource(R.string.common_cancel)
    if (playlists.isEmpty()) {
        TakiSheet(
            title = title,
            dismissLabel = dismissLabel,
            onDismiss = onDismiss,
            bottomContentInset = bottomContentInset,
            sheetTestTag = ADD_TO_PLAYLIST_SHEET_TEST_TAG,
        ) {
            Text(
                text = stringResource(R.string.select_playlist_empty),
                style = TakiTheme.type.body,
                modifier = Modifier
                    .testTag(TAKI_SHEET_EMPTY_TEXT_TEST_TAG)
                    .padding(vertical = TakiTheme.spacing.sm),
            )
        }
        return
    }
    val options = remember(playlists) { playlists.map { TakiPickerOption(it.id, it.name) } }
    TakiPickerSheet(
        title = title,
        options = options,
        onSelect = onSelect,
        onDismiss = onDismiss,
        dismissLabel = dismissLabel,
        bottomContentInset = bottomContentInset,
        sheetTestTag = ADD_TO_PLAYLIST_SHEET_TEST_TAG,
    )
}
