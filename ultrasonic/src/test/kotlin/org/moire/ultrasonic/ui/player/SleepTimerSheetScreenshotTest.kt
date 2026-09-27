/*
 * SleepTimerSheetScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the Sleep Timer sheet (issue #10 phase 4K6): inactive, a timed active
 * state and the end-of-song active state - the three visually distinct states, per the task's
 * "avoid excessive goldens" guidance. Deterministic - a fixed `nowMs`, no artwork network (the
 * sheet never shows artwork). Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h700dp-xxhdpi")
class SleepTimerSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .fillMaxSize()
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Composable
    private fun Sheet(state: SleepTimerState) {
        Box(Modifier.fillMaxSize()) {
            SleepTimerSheet(
                visible = true,
                state = state,
                hasCurrentTrack = true,
                actions = SleepTimerActions.Noop,
                nowMs = { 0L },
            )
        }
    }

    @Test
    fun sleepTimerInactive() = capture("sleep_timer_inactive") { Sheet(SleepTimerState.Off) }

    @Test
    fun sleepTimerTimedActive() = capture("sleep_timer_timed_active") {
        Sheet(SleepTimerState.Duration(deadlineElapsedRealtime = 28 * 60_000L, presetMinutes = 30))
    }

    @Test
    fun sleepTimerEndOfSongActive() = capture("sleep_timer_end_of_song_active") {
        Sheet(SleepTimerState.EndOfTrack)
    }
}
