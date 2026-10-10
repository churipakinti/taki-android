/*
 * SettingsConfirmSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import org.moire.ultrasonic.ui.components.takiSheetPanelTapSwallow
import org.moire.ultrasonic.ui.components.TakiBackHandler
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the sheet and its primary action. */
const val SETTINGS_CONFIRM_SHEET_TEST_TAG = "settings_confirm_sheet"
const val SETTINGS_CONFIRM_SCRIM_TEST_TAG = "settings_confirm_scrim"
const val SETTINGS_CONFIRM_ACTION_TEST_TAG = "settings_confirm_action"

/**
 * One generic confirmation sheet standing in for three legacy `ConfirmationDialog`-based
 * `AlertDialog`s (issue #10 phase 5A4): clear-downloads, clear-image-cache, and the debug-log
 * "keep or delete these files" prompt - same copy, same two-button shape, only the labels and
 * the gated [SettingsViewModel.onConfirm] branch differ per [SettingsOverlay.Confirm.action].
 * Same scrim + sliding panel pattern as `DeleteServerSheet`.
 */
@Composable
fun SettingsConfirmSheet(
    overlay: SettingsOverlay.Confirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
) {
    TakiBackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        val dismissInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .matchParentSize()
                .testTag(SETTINGS_CONFIRM_SCRIM_TEST_TAG)
                .background(TakiTheme.colors.black.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClickLabel = stringResource(overlay.dismissLabelRes),
                    role = Role.Button,
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
                .background(TakiTheme.colors.surface)
                .testTag(SETTINGS_CONFIRM_SHEET_TEST_TAG)
                .takiSheetPanelTapSwallow()
                .padding(horizontal = TakiTheme.spacing.xl)
                .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.xl + bottomContentInset),
        ) {
            DragHandle()
            Spacer(Modifier.height(TakiTheme.spacing.md))
            Text(
                text = stringResource(overlay.titleRes),
                style = TakiTheme.type.title,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(TakiTheme.spacing.sm))
            Text(
                text = stringResource(overlay.messageRes, *overlay.messageArgs.toTypedArray()),
                style = TakiTheme.type.body,
            )
            Spacer(Modifier.height(TakiTheme.spacing.lg))
            ConfirmSheetActions(overlay, onConfirm, onDismiss)
        }
    }
}

@Composable
private fun ConfirmSheetActions(
    overlay: SettingsOverlay.Confirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
        ) {
            Text(text = stringResource(overlay.dismissLabelRes), style = TakiTheme.type.titleSmall)
        }
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .testTag(SETTINGS_CONFIRM_ACTION_TEST_TAG)
                .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
            colors = ButtonDefaults.buttonColors(
                containerColor = TakiTheme.colors.accent,
                contentColor = TakiTheme.colors.onAccent,
            ),
        ) {
            Text(text = stringResource(overlay.confirmLabelRes), style = TakiTheme.type.titleSmall)
        }
    }
}

/** Purely decorative - the scrim tap and system back already dismiss the sheet. */
@Composable
private fun DragHandle() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = TakiTheme.spacing.xs),
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
