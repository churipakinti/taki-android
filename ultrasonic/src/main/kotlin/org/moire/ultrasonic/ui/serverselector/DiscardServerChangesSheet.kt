/*
 * DiscardServerChangesSheet.kt
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
const val DISCARD_SERVER_SHEET_TEST_TAG = "discard_server_changes_sheet"
const val DISCARD_SERVER_SCRIM_TEST_TAG = "discard_server_changes_scrim"
const val DISCARD_SERVER_CONFIRM_TEST_TAG = "discard_server_changes_confirm"

private const val SHEET_ANIMATION_MS = 200

/**
 * The leave-with-unsaved-changes confirmation (issue #10 phase 5A3), replacing the legacy
 * `ErrorDialog`-based `AlertDialog` `EditServerFragment.finishActivity()` showed. Same scrim +
 * sliding panel pattern every other migrated transient overlay uses. Unlike the legacy screen -
 * where only the system Back gesture triggered this dialog, while the shared toolbar's own back
 * arrow silently discarded changes with no prompt (a real gap found in the phase 5A3 audit) -
 * both the header back action and system Back route through the same `EditServerViewModel
 * .requestBack()` dirty-check here, so this sheet appears consistently either way.
 */
@Composable
fun BoxScope.DiscardServerChangesSheet(
    visible: Boolean,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
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
                    .testTag(DISCARD_SERVER_SCRIM_TEST_TAG)
                    .background(TakiTheme.colors.black.copy(alpha = SCRIM_ALPHA))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClickLabel = stringResource(R.string.common_navigate_back),
                        role = Role.Button,
                        onClick = onCancel,
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(SHEET_ANIMATION_MS)) { it },
                exit = slideOutVertically(tween(SHEET_ANIMATION_MS)) { it },
            ) {
                DiscardServerChangesSheetContent(onDiscard, onCancel, bottomContentInset)
            }
        }
    }
}

@Composable
private fun DiscardServerChangesSheetContent(
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    bottomContentInset: Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
            .background(TakiTheme.colors.surface)
            .testTag(DISCARD_SERVER_SHEET_TEST_TAG)
            .padding(horizontal = TakiTheme.spacing.xl)
            .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.xl + bottomContentInset),
    ) {
        DragHandle()
        Spacer(Modifier.height(TakiTheme.spacing.md))
        Text(
            text = stringResource(R.string.common_confirm),
            style = TakiTheme.type.title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(TakiTheme.spacing.sm))
        Text(
            text = stringResource(R.string.server_editor_leave_confirmation),
            style = TakiTheme.type.body,
        )
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
            ) {
                Text(text = stringResource(R.string.common_cancel), style = TakiTheme.type.titleSmall)
            }
            Spacer(Modifier.width(TakiTheme.spacing.sm))
            Button(
                onClick = onDiscard,
                modifier = Modifier
                    .testTag(DISCARD_SERVER_CONFIRM_TEST_TAG)
                    .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TakiTheme.colors.accent,
                    contentColor = TakiTheme.colors.onAccent,
                ),
            ) {
                Text(text = stringResource(R.string.common_ok), style = TakiTheme.type.titleSmall)
            }
        }
    }
}

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
