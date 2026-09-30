/*
 * DeleteServerSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the sheet and its actions. */
const val DELETE_SERVER_SHEET_TEST_TAG = "delete_server_sheet"
const val DELETE_SERVER_SCRIM_TEST_TAG = "delete_server_scrim"
const val DELETE_SERVER_NAME_TEST_TAG = "delete_server_name"
const val DELETE_SERVER_CONFIRM_TEST_TAG = "delete_server_confirm"

private const val SHEET_ANIMATION_MS = 200

/**
 * The delete-server confirmation (post-issue-#10 residual migration, phase 5A2), replacing the
 * legacy `ErrorDialog`-based `AlertDialog` `ServerSelectorFragment.deleteServerById` showed. Same
 * plain scrim + sliding panel pattern every other migrated transient overlay uses (`SleepTimerSheet`
 * /`SavePlaylistSheet`/`CreatePlaylistNameSheet`/`RenamePlaylistSheet`). Presentation-only - no
 * ViewModel of its own; [ServerSelectorScreen]'s own `ServerSelectorViewModel` owns
 * `pendingDelete` and the actual deletion sequence (see its kdoc for the exact legacy-preserved
 * side effects). Copy is the legacy dialog's own title/message verbatim, not new alarming text.
 */
@Composable
fun BoxScope.DeleteServerSheet(
    visible: Boolean,
    serverName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
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
                    .testTag(DELETE_SERVER_SCRIM_TEST_TAG)
                    .background(TakiTheme.colors.black.copy(alpha = SCRIM_ALPHA))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClickLabel = stringResource(R.string.common_navigate_back),
                        role = Role.Button,
                        onClick = onDismiss,
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(SHEET_ANIMATION_MS)) { it },
                exit = slideOutVertically(tween(SHEET_ANIMATION_MS)) { it },
            ) {
                DeleteServerSheetContent(serverName, onConfirm, onDismiss, bottomContentInset)
            }
        }
    }
}

@Composable
private fun DeleteServerSheetContent(
    serverName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
            .background(TakiTheme.colors.surface)
            .testTag(DELETE_SERVER_SHEET_TEST_TAG)
            .padding(horizontal = TakiTheme.spacing.xl)
            .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.xl + bottomContentInset),
    ) {
        DragHandle()
        Spacer(Modifier.height(TakiTheme.spacing.md))
        Text(
            text = stringResource(R.string.server_menu_delete),
            style = TakiTheme.type.title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(TakiTheme.spacing.sm))
        Text(
            text = stringResource(R.string.server_selector_delete_confirmation),
            style = TakiTheme.type.body,
        )
        Spacer(Modifier.height(TakiTheme.spacing.sm))
        Text(
            text = serverName,
            style = TakiTheme.type.titleSmall,
            modifier = Modifier.testTag(DELETE_SERVER_NAME_TEST_TAG),
        )
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
            ) {
                Text(text = stringResource(R.string.common_cancel), style = TakiTheme.type.titleSmall)
            }
            Spacer(Modifier.width(TakiTheme.spacing.sm))
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .testTag(DELETE_SERVER_CONFIRM_TEST_TAG)
                    .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TakiTheme.colors.accent,
                    contentColor = TakiTheme.colors.onAccent,
                ),
            ) {
                Text(text = stringResource(R.string.common_delete), style = TakiTheme.type.titleSmall)
            }
        }
    }
}

/** Purely decorative - the scrim tap and system back already dismiss the sheet. */
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
