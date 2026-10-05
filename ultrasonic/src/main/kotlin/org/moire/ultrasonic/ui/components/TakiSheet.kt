/*
 * TakiSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.ui.theme.TakiTheme

/** The scrim of a [TakiSheet] whose panel carries [sheetTestTag]. */
fun takiSheetScrimTestTag(sheetTestTag: String) = "${sheetTestTag}_scrim"

/** How much of the host's height a sheet's scrollable content may use before it scrolls. */
const val TAKI_SHEET_CONTENT_HEIGHT_FRACTION = 0.75f

private const val SCRIM_ALPHA = 0.6f
private const val DRAG_HANDLE_ALPHA = 0.4f
private val SHEET_CORNER_RADIUS = 20.dp // taki-raw-ok: radius_lg's value, top corners only

/**
 * The one transient bottom-sheet chrome shared by every overlay migrated in phase 5A6 (list
 * pickers, confirmations, info/message sheets, the Library hub): a Taki scrim, a rounded surface
 * panel anchored to the bottom, a decorative drag handle and a heading title - the same visual
 * language as the earlier per-feature sheets (`SettingsChoiceSheet`, `DeleteServerSheet`, ...).
 *
 * Behaviour contract:
 *  - A scrim tap, or the system Back button ([TakiBackHandler]), calls [onDismiss] - and nothing
 *    else; dismissing never performs the sheet's action.
 *  - The panel's bottom padding is [bottomContentInset] only. Pass the host's real floating-chrome
 *    inset when the host renders behind the mini-player/bottom nav, and zero (or a small
 *    breathing room) when the nav host is already padded above it - never both (the Equalizer
 *    5A5 double-inset regression).
 *  - [content] receives the maximum height its scrollable part may use, 75% of the host's
 *    available height, so a long list scrolls inside the sheet instead of running off the screen.
 *
 * Compose is presentation only: this holds no state about *what* the sheet is for.
 */
@Composable
fun TakiSheet(
    title: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    bottomContentInset: Dp,
    sheetTestTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(maxContentHeight: Dp) -> Unit,
) {
    TakiBackHandler(onBack = onDismiss)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxContentHeight = maxHeight * TAKI_SHEET_CONTENT_HEIGHT_FRACTION
        val dismissInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .matchParentSize()
                .testTag(takiSheetScrimTestTag(sheetTestTag))
                .background(TakiTheme.colors.black.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClickLabel = dismissLabel,
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
                .testTag(sheetTestTag)
                .padding(horizontal = TakiTheme.spacing.xl)
                .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.sm + bottomContentInset),
        ) {
            TakiSheetDragHandle()
            Text(
                text = title,
                style = TakiTheme.type.title,
                modifier = Modifier.semantics { heading() }.padding(bottom = TakiTheme.spacing.sm),
            )
            content(maxContentHeight)
        }
    }
}

/** Purely decorative - the scrim tap and system Back already dismiss the sheet. */
@Composable
private fun TakiSheetDragHandle() {
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

/**
 * Routes the system Back gesture/button to [onBack] while this composable is in the composition,
 * ahead of the Fragment/NavController callbacks registered earlier - so Back closes the overlay
 * before it would navigate away from the owning screen. Uses the Activity's dispatcher found on
 * the view tree (no extra dependency, and nothing outlives the composition).
 */
@Composable
fun TakiBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val view = LocalView.current
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(view, enabled) {
        val owner = view.findViewTreeOnBackPressedDispatcherOwner()
        if (!enabled || owner == null) {
            onDispose { }
        } else {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = currentOnBack()
            }
            owner.onBackPressedDispatcher.addCallback(owner, callback)
            onDispose { callback.remove() }
        }
    }
}
