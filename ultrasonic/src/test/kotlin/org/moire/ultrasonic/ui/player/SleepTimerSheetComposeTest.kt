/*
 * SleepTimerSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TOUCH_TARGET_MIN_DP = 48

/**
 * The Sleep Timer sheet (issue #10 phase 4K6): inactive/timed/end-of-song state, preset and
 * end-of-song selection, cancellation, scrim/back dismissal, and accessibility (selected state,
 * touch targets). [SleepTimerSheet] never computes a deadline itself - [nowMs] is a fixed fake
 * here, exactly like `positionMs` in the Lyrics screen tests, so "remaining" text is
 * deterministic without a real clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class SleepTimerSheetComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun noopActions(
        onSelectDuration: (Int) -> Unit = {},
        onSelectEndOfTrack: () -> Unit = {},
        onCancel: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = SleepTimerActions(onSelectDuration, onSelectEndOfTrack, onCancel, onDismiss)

    private fun setContent(
        state: SleepTimerState = SleepTimerState.Off,
        hasCurrentTrack: Boolean = true,
        actions: SleepTimerActions = noopActions(),
        nowMs: () -> Long = { 0L },
    ) {
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) {
                    SleepTimerSheet(
                        visible = true,
                        state = state,
                        hasCurrentTrack = hasCurrentTrack,
                        actions = actions,
                        nowMs = nowMs,
                    )
                }
            }
        }
    }

    // --- state --------------------------------------------------------------------------------

    @Test
    fun `the inactive state shows the presets and no active timer`() {
        setContent(state = SleepTimerState.Off)

        compose.onNodeWithText("Sleep timer").assertIsDisplayed()
        compose.onNodeWithText("No active timer").assertIsDisplayed()
        SLEEP_TIMER_PRESET_MINUTES.forEach { minutes ->
            compose.onNodeWithText("$minutes minutes").assertIsDisplayed()
        }
        compose.onNodeWithText("End of current song").assertIsDisplayed()
    }

    @Test
    fun `a timed active state shows the remaining minutes and the selected preset`() {
        // deadline - now = exactly 12 minutes.
        setContent(
            state = SleepTimerState.Duration(deadlineElapsedRealtime = 12 * 60_000L, presetMinutes = 30),
            nowMs = { 0L },
        )

        compose.onNodeWithText("12 min remaining").assertIsDisplayed()
        compose.onNodeWithText("30 minutes").assertIsSelected()
        compose.onNodeWithText("15 minutes").assertIsNotSelected()
    }

    @Test
    fun `an end-of-song active state shows the stops-after-song status and the selected row`() {
        setContent(state = SleepTimerState.EndOfTrack)

        compose.onNodeWithText("Stops after this song").assertIsDisplayed()
        compose.onNodeWithText("End of current song").assertIsSelected()
    }

    @Test
    fun `no preset is selected when the timer is off`() {
        setContent(state = SleepTimerState.Off)

        SLEEP_TIMER_PRESET_MINUTES.forEach { minutes ->
            compose.onNodeWithText("$minutes minutes").assertIsNotSelected()
        }
        compose.onNodeWithText("End of current song").assertIsNotSelected()
    }

    // --- behavior -------------------------------------------------------------------------------

    @Test
    fun `selecting a preset calls onSelectDuration with that exact preset`() {
        var selected: Int? = null
        setContent(actions = noopActions(onSelectDuration = { selected = it }))

        compose.onNodeWithText("30 minutes").performClick()
        assertEquals(30, selected)
    }

    @Test
    fun `selecting end of song calls onSelectEndOfTrack`() {
        var called = false
        setContent(actions = noopActions(onSelectEndOfTrack = { called = true }))

        compose.onNodeWithText("End of current song").performClick()
        assertTrue(called)
    }

    @Test
    fun `cancel is only shown while a timer is active, and calls onCancel`() {
        var cancelled = false
        setContent(
            state = SleepTimerState.Duration(deadlineElapsedRealtime = 60_000L, presetMinutes = 15),
            actions = noopActions(onCancel = { cancelled = true }),
        )

        compose.onNodeWithText("Cancel timer").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun `there is no cancel action while the timer is off`() {
        setContent(state = SleepTimerState.Off)
        compose.onNodeWithTag(SLEEP_TIMER_CANCEL_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `tapping the scrim calls onDismiss`() {
        var dismissed = false
        setContent(actions = noopActions(onDismiss = { dismissed = true }))

        compose.onNodeWithTag(SLEEP_TIMER_SCRIM_TEST_TAG).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `end of song is disabled when there is no current track`() {
        setContent(hasCurrentTrack = false)
        compose.onNodeWithText("End of current song").assertIsNotEnabled()
    }

    // --- accessibility --------------------------------------------------------------------------

    @Test
    fun `every preset and the end-of-song row meet the 48dp touch target minimum`() {
        setContent()
        SLEEP_TIMER_PRESET_MINUTES.forEach { minutes ->
            compose.onNodeWithText("$minutes minutes")
                .assertHeightIsAtLeast(TOUCH_TARGET_MIN_DP.dp)
        }
        compose.onNodeWithText("End of current song").assertHeightIsAtLeast(TOUCH_TARGET_MIN_DP.dp)
    }

    @Test
    fun `the cancel action meets the touch target minimum and is reachable`() {
        setContent(state = SleepTimerState.Duration(deadlineElapsedRealtime = 60_000L, presetMinutes = 15))
        compose.onNodeWithTag(SLEEP_TIMER_CANCEL_TEST_TAG)
            .assertHeightIsAtLeast(TOUCH_TARGET_MIN_DP.dp)
            .assertHasClickAction()
    }

    @Test
    fun `the sheet title is announced as a heading`() {
        setContent()
        val isHeading = SemanticsMatcher("is heading") {
            it.config.contains(SemanticsProperties.Heading)
        }
        compose.onNodeWithText("Sleep timer").assert(isHeading)
    }
}
