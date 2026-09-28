/*
 * CreatePlaylistNameSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.TakiTextField
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the sheet and its actions. */
const val CREATE_PLAYLIST_NAME_SHEET_TEST_TAG = "create_playlist_name_sheet"
const val CREATE_PLAYLIST_NAME_FIELD_TEST_TAG = "create_playlist_name_field"
const val CREATE_PLAYLIST_NAME_SCRIM_TEST_TAG = "create_playlist_name_scrim"

private const val SHEET_ANIMATION_MS = 200

/**
 * What the sheet does (issue #10 phase 4M3). Validation and the resulting navigation to
 * `createPlaylistFragment` both stay in [org.moire.ultrasonic.fragment.PlaylistListFragment],
 * unchanged from the legacy `AlertDialog`: [onCreate] is called on every tap of the primary
 * action / every IME submit, and the host decides whether that closes the sheet (valid,
 * trimmed, non-blank name - matches `NavigationGraphDirections.toCreatePlaylist`) or instead sets
 * an error message and keeps it open (blank after trim) - this file owns no validation logic of
 * its own, only presentation.
 */
data class CreatePlaylistNameActions(
    val onNameChange: (String) -> Unit,
    val onCreate: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        val Noop = CreatePlaylistNameActions(
            onNameChange = {},
            onCreate = {},
            onDismiss = {},
        )
    }
}

/**
 * A compact bottom-sheet-style overlay (issue #10 phase 4M3) replacing the legacy `AlertDialog`
 * (`R.layout.create_playlist`) that collects and validates the name for a new playlist before
 * navigating to the Compose `createPlaylistFragment` track picker. The same plain scrim + sliding
 * panel pattern [org.moire.ultrasonic.ui.player.SleepTimerSheet]/
 * [org.moire.ultrasonic.ui.player.SavePlaylistSheet] use, composed as the last child of
 * [org.moire.ultrasonic.ui.playlistlist.PlaylistListScreen]'s own root `Box` - transient UI over
 * the Playlists list, not a new destination. [name]/[errorMessage] are host-owned; this file owns
 * no text-field or validation state of its own.
 */
@Composable
fun BoxScope.CreatePlaylistNameSheet(
    visible: Boolean,
    name: String,
    errorMessage: String?,
    actions: CreatePlaylistNameActions,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(SHEET_ANIMATION_MS)),
        exit = fadeOut(tween(SHEET_ANIMATION_MS)),
    ) {
        Box(Modifier.fillMaxSize()) {
            val dismissInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(CREATE_PLAYLIST_NAME_SCRIM_TEST_TAG)
                    .background(TakiTheme.colors.black.copy(alpha = SCRIM_ALPHA))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClickLabel = stringResource(R.string.common_navigate_back),
                        role = Role.Button,
                        onClick = actions.onDismiss,
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(SHEET_ANIMATION_MS)) { it },
                exit = slideOutVertically(tween(SHEET_ANIMATION_MS)) { it },
            ) {
                CreatePlaylistNameSheetContent(name, errorMessage, actions)
            }
        }
    }
}

@Composable
private fun CreatePlaylistNameSheetContent(
    name: String,
    errorMessage: String?,
    actions: CreatePlaylistNameActions,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
            .background(TakiTheme.colors.surface)
            .testTag(CREATE_PLAYLIST_NAME_SHEET_TEST_TAG)
            .navigationBarsPadding()
            .padding(horizontal = TakiTheme.spacing.xl)
            .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.xl),
    ) {
        DragHandle()
        Spacer(Modifier.height(TakiTheme.spacing.md))
        Text(
            text = stringResource(R.string.playlist_create),
            style = TakiTheme.type.title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        TakiTextField(
            value = name,
            onValueChange = actions.onNameChange,
            label = stringResource(R.string.download_playlist_name),
            modifier = Modifier.testTag(CREATE_PLAYLIST_NAME_FIELD_TEST_TAG),
            isError = errorMessage != null,
            errorMessage = errorMessage,
            imeAction = ImeAction.Done,
            onImeAction = actions.onCreate,
        )
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = actions.onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
            ) {
                Text(text = stringResource(R.string.common_cancel), style = TakiTheme.type.titleSmall)
            }
            Spacer(Modifier.width(TakiTheme.spacing.sm))
            Button(
                onClick = actions.onCreate,
                modifier = Modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TakiTheme.colors.accent,
                    contentColor = TakiTheme.colors.onAccent,
                ),
            ) {
                Text(text = stringResource(R.string.playlist_create_action), style = TakiTheme.type.titleSmall)
            }
        }
    }
}

/** Purely decorative, matching [org.moire.ultrasonic.ui.player.SleepTimerSheet]'s own drag
 *  handle - the scrim tap and system back already dismiss the sheet. */
@Composable
private fun DragHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = TakiTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(TakiTheme.spacing.xxl)
                .height(TakiTheme.spacing.xs)
                .clip(TakiTheme.shapes.xs)
                .background(TakiTheme.colors.gray.copy(alpha = DRAG_HANDLE_ALPHA)),
        )
    }
}

private const val SCRIM_ALPHA = 0.6f
private const val DRAG_HANDLE_ALPHA = 0.4f
private val SHEET_CORNER_RADIUS = 20.dp // taki-raw-ok: radius_lg's value, top corners only
