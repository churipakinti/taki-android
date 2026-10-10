/*
 * SleepTimerSheet.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import android.os.SystemClock
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.components.takiSheetPanelTapSwallow
import org.moire.ultrasonic.ui.components.TakiBackHandler
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests find the sheet, a preset chip and the cancel action. */
const val SLEEP_TIMER_SHEET_TEST_TAG = "sleep_timer_sheet"
const val SLEEP_TIMER_CANCEL_TEST_TAG = "sleep_timer_cancel"
const val SLEEP_TIMER_END_OF_SONG_TEST_TAG = "sleep_timer_end_of_song"
const val SLEEP_TIMER_SCRIM_TEST_TAG = "sleep_timer_scrim"

/** The exact legacy `PlayerFragment` preset minutes (issue #10 phase 4K6) - unchanged values,
 *  only the presentation moved from a single-choice `AlertDialog` to this sheet. */
val SLEEP_TIMER_PRESET_MINUTES = listOf(15, 30, 45, 60)

private const val MILLIS_PER_MINUTE = 60_000L
private const val SHEET_ANIMATION_MS = 200
private const val PRESET_GRID_COLUMNS = 2

/**
 * What the sheet does, dispatched straight to the existing [org.moire.ultrasonic.service
 * .SleepTimerController] through `PlaybackUiStateHolder` - this file owns no timer state and no
 * command reimplements the controller's semantics (end-of-song vs timed, deadline math, manual
 * seek not counting as completion all stay exactly where they were).
 */
data class SleepTimerActions(
    val onSelectDuration: (minutes: Int) -> Unit,
    val onSelectEndOfTrack: () -> Unit,
    val onCancel: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        val Noop = SleepTimerActions(
            onSelectDuration = {},
            onSelectEndOfTrack = {},
            onCancel = {},
            onDismiss = {},
        )
    }
}

/**
 * A compact bottom-sheet-style overlay (issue #10 phase 4K6) replacing the legacy single-choice
 * `AlertDialog` sleep timer picker. Not a system dialog window - a plain scrim + sliding panel
 * composed as the last child of the same `Box` Now Playing (or Up Next) renders in, so it is
 * transient UI over the current player surface rather than a new destination. [state] is the
 * same read-only `SleepTimerState` Now Playing's utility row already projects; this sheet never
 * computes a deadline or owns a countdown - [SleepTimerState.Duration.remainingMs] is read once
 * per composition, exactly like the utility row's own content description.
 */
@Composable
fun BoxScope.SleepTimerSheet(
    visible: Boolean,
    state: SleepTimerState,
    hasCurrentTrack: Boolean,
    actions: SleepTimerActions,
    modifier: Modifier = Modifier,
    nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    TakiBackHandler(enabled = visible, onBack = actions.onDismiss)
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
                    .testTag(SLEEP_TIMER_SCRIM_TEST_TAG)
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
                SleepTimerSheetContent(state, hasCurrentTrack, actions, nowMs)
            }
        }
    }
}

@Composable
private fun SleepTimerSheetContent(
    state: SleepTimerState,
    hasCurrentTrack: Boolean,
    actions: SleepTimerActions,
    nowMs: () -> Long,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
            .background(TakiTheme.colors.surface)
            .testTag(SLEEP_TIMER_SHEET_TEST_TAG)
            .takiSheetPanelTapSwallow()
            .navigationBarsPadding()
            .padding(horizontal = TakiTheme.spacing.xl)
            .padding(top = TakiTheme.spacing.sm, bottom = TakiTheme.spacing.xl),
    ) {
        DragHandle()
        Spacer(Modifier.height(TakiTheme.spacing.md))
        Text(
            text = stringResource(R.string.sleep_timer_title),
            style = TakiTheme.type.title,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(TakiTheme.spacing.xxs))
        StatusLine(state, nowMs)
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        // A 2x2 grid, not a single row of four: "45 minutes"/"60 minutes" (the plural label,
        // kept verbatim rather than abbreviated to "45m" for a clearer accessibility label)
        // does not fit four-across on a phone width.
        SLEEP_TIMER_PRESET_MINUTES.chunked(PRESET_GRID_COLUMNS).forEach { rowMinutes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.sm),
            ) {
                rowMinutes.forEach { minutes ->
                    val label = pluralStringResource(R.plurals.sleep_timer_option_minutes, minutes, minutes)
                    PresetChip(
                        label = label,
                        selected = state is SleepTimerState.Duration && state.presetMinutes == minutes,
                        onClick = { actions.onSelectDuration(minutes) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(TakiTheme.spacing.sm))
        }
        PresetChip(
            label = stringResource(R.string.sleep_timer_end_of_song),
            selected = state is SleepTimerState.EndOfTrack,
            onClick = actions.onSelectEndOfTrack,
            enabled = hasCurrentTrack,
            modifier = Modifier.testTag(SLEEP_TIMER_END_OF_SONG_TEST_TAG),
        )
        if (state != SleepTimerState.Off) {
            Spacer(Modifier.height(TakiTheme.spacing.lg))
            TextButton(
                onClick = actions.onCancel,
                modifier = Modifier
                    .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
                    .testTag(SLEEP_TIMER_CANCEL_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.sleep_timer_cancel), style = TakiTheme.type.titleSmall)
            }
        }
    }
}

/** Purely decorative (issue #10 phase 4K6 accessibility requirement) - the sheet's own scrim tap
 *  and system back already dismiss it, so the handle carries no semantics of its own. */
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

/** [SleepTimerState.Off] -> "No active timer"; [SleepTimerState.EndOfTrack] -> "Stops after this
 *  song"; [SleepTimerState.Duration] -> "N min remaining", [nowMs] read once per composition
 *  (never polled - the sheet is only open briefly and closes on any selection). */
@Composable
private fun StatusLine(state: SleepTimerState, nowMs: () -> Long) {
    val text = when (state) {
        SleepTimerState.Off -> stringResource(R.string.sleep_timer_no_active)
        SleepTimerState.EndOfTrack -> stringResource(R.string.sleep_timer_stops_after_song)
        is SleepTimerState.Duration -> {
            val remainingMinutes = ceilMinutes(state.remainingMs(nowMs()))
            pluralStringResource(R.plurals.sleep_timer_remaining_full, remainingMinutes, remainingMinutes)
        }
    }
    val color = if (state == SleepTimerState.Off) TakiTheme.colors.gray else TakiTheme.colors.accent
    Text(text = text, style = TakiTheme.type.caption.copy(color = color))
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = TakiTheme.type.titleSmall) },
        modifier = modifier
            .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
            .semantics { this.selected = selected },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = TakiTheme.colors.surfaceHigh,
            labelColor = TakiTheme.colors.ivory,
            selectedContainerColor = TakiTheme.colors.selectedContainer,
            selectedLabelColor = TakiTheme.colors.accent,
        ),
    )
}

private const val SCRIM_ALPHA = 0.6f
private const val DRAG_HANDLE_ALPHA = 0.4f
private val SHEET_CORNER_RADIUS = 20.dp // taki-raw-ok: radius_lg's value, top corners only

/** Shared with [NowPlayingScreen]'s own sleep-timer content description - one rounding rule. */
internal fun ceilMinutes(remainingMs: Long): Int =
    ((remainingMs + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)
