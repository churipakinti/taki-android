/*
 * PlaylistInfoSheets.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiMessageSheet
import org.moire.ultrasonic.ui.components.TakiSheet
import org.moire.ultrasonic.ui.components.TakiSheetActions
import org.moire.ultrasonic.ui.components.TakiTextField
import org.moire.ultrasonic.ui.components.linkifyWebUrls
import org.moire.ultrasonic.ui.theme.TakiTheme

const val PLAYLIST_INFO_SHEET_TEST_TAG = "playlist_info_sheet"
const val UPDATE_PLAYLIST_INFO_SHEET_TEST_TAG = "update_playlist_info_sheet"
const val UPDATE_PLAYLIST_NAME_FIELD_TEST_TAG = "update_playlist_name_field"
const val UPDATE_PLAYLIST_COMMENT_FIELD_TEST_TAG = "update_playlist_comment_field"
const val UPDATE_PLAYLIST_PUBLIC_TEST_TAG = "update_playlist_public"

/**
 * The fields the legacy "playlist info" `InfoDialog` printed, exactly. [songCount] and [created]
 * are the server's own strings; [isPublic] is `null` when the server did not say.
 */
data class PlaylistInfoUiState(
    val name: String,
    val owner: String,
    val comment: String,
    val songCount: String,
    val isPublic: Boolean?,
    val created: String,
)

/**
 * The playlist info text, line for line as the legacy dialog built it: Owner, Comments, Song Count
 * and - only when the server reports a public flag - Public and Creation Date (the server's
 * `T` date separator shown as a space). Web URLs in the text are tappable, like the legacy
 * `Linkify.WEB_URLS` pass.
 */
@Composable
fun playlistInfoLines(info: PlaylistInfoUiState): List<String> = buildList {
    add(stringResource(R.string.playlist_info_owner, info.owner))
    add(stringResource(R.string.playlist_info_comments, info.comment))
    add(stringResource(R.string.playlist_info_song_count, info.songCount))
    if (info.isPublic != null) {
        add(stringResource(R.string.playlist_info_public, info.isPublic.toString()))
        add(stringResource(R.string.playlist_info_created, info.created.replace('T', ' ')))
    }
}

/** The Compose replacement for the playlist-info `InfoDialog` (issue #10 phase 5A6). */
@Composable
fun PlaylistInfoSheet(
    info: PlaylistInfoUiState,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    TakiMessageSheet(
        title = info.name,
        message = linkifyWebUrls(playlistInfoLines(info).joinToString("\n"), TakiTheme.colors.accent),
        okLabel = stringResource(R.string.common_ok),
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = PLAYLIST_INFO_SHEET_TEST_TAG,
    )
}

/**
 * The Compose replacement for the "Update Information" `ConfirmationDialog` + `update_playlist`
 * form (issue #10 phase 5A6): name, comment and a Public checkbox, pre-filled from the playlist.
 * The checkbox is disabled when the server did not report a public flag ([initialPublic] `null`),
 * and then reports `false`, as the legacy disabled checkbox did. No validation - exactly like the
 * legacy dialog, [onConfirm] receives the fields as typed. [onConfirm] fires at most once;
 * dismissing (Cancel, scrim, Back) changes nothing.
 */
@Composable
fun UpdatePlaylistInfoSheet(
    initialName: String,
    initialComment: String,
    initialPublic: Boolean?,
    onConfirm: (name: String, comment: String, isPublic: Boolean) -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var comment by remember(initialComment) { mutableStateOf(initialComment) }
    var isPublic by remember(initialPublic) { mutableStateOf(initialPublic ?: false) }
    var consumed by remember { mutableStateOf(false) }
    val submit = {
        if (!consumed) {
            consumed = true
            onConfirm(name, comment, isPublic)
        }
    }
    TakiSheet(
        title = stringResource(R.string.playlist_update_info),
        dismissLabel = stringResource(R.string.common_cancel),
        onDismiss = onDismiss,
        bottomContentInset = bottomContentInset,
        sheetTestTag = UPDATE_PLAYLIST_INFO_SHEET_TEST_TAG,
    ) { maxContentHeight ->
        UpdatePlaylistInfoForm(
            name = name,
            onNameChange = { name = it },
            comment = comment,
            onCommentChange = { comment = it },
            isPublic = isPublic,
            publicEnabled = initialPublic != null,
            onPublicChange = { isPublic = it },
            modifier = Modifier.heightIn(max = maxContentHeight).verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        TakiSheetActions(
            confirmLabel = stringResource(R.string.common_ok),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { submit() },
            onDismiss = onDismiss,
        )
    }
}

/** The three editable fields of the "Update Information" form. */
@Composable
private fun UpdatePlaylistInfoForm(
    name: String,
    onNameChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    isPublic: Boolean,
    publicEnabled: Boolean,
    onPublicChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TakiTextField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.common_name),
            modifier = Modifier.testTag(UPDATE_PLAYLIST_NAME_FIELD_TEST_TAG),
        )
        Spacer(Modifier.height(TakiTheme.spacing.md))
        TakiTextField(
            value = comment,
            onValueChange = onCommentChange,
            label = stringResource(R.string.common_comment),
            modifier = Modifier.testTag(UPDATE_PLAYLIST_COMMENT_FIELD_TEST_TAG),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TakiTheme.dimensions.touchTargetMin)
                .testTag(UPDATE_PLAYLIST_PUBLIC_TEST_TAG)
                .toggleable(
                    value = isPublic,
                    enabled = publicEnabled,
                    role = Role.Checkbox,
                    onValueChange = onPublicChange,
                )
                .semantics(mergeDescendants = true) {}
                .padding(top = TakiTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isPublic,
                onCheckedChange = null,
                enabled = publicEnabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = TakiTheme.colors.accent,
                    checkmarkColor = TakiTheme.colors.onAccent,
                ),
            )
            Text(text = stringResource(R.string.common_public), style = TakiTheme.type.body)
        }
    }
}
