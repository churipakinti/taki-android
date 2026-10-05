/*
 * EqualizerScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Equalizer (issue #10 phase 5A5): enabled with the five bands a typical
 * device reports, disabled, the preset sheet, and the unavailable state. Fully deterministic -
 * fake [EqualizerUiState], no audio runtime. The capture tags are deliberately distinct from the
 * screen's own test tags. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class EqualizerScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val ranges = listOf(30 to 120, 120 to 460, 460 to 1800, 1800 to 7000, 7000 to 20000)
    private val levels = listOf(300, 0, -500, 150, 300)

    private val bands = ranges.mapIndexed { i, (low, high) ->
        EqualizerBandUiState(i, low, high, minLevel = -1500, maxLevel = 1500, level = levels[i])
    }

    private val presets = listOf("Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop")
        .mapIndexed { i, name -> EqualizerPresetUiState(i, name) }

    private val enabled = EqualizerUiState(
        controllerAvailable = true,
        enabled = true,
        bands = bands,
        presets = presets,
        currentPresetIndex = 2,
    )

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .heightIn(max = 1200.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: EqualizerUiState): @Composable () -> Unit =
        { EqualizerScreen(state = state, actions = EqualizerActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun equalizerEnabled() = capture("equalizer_screen_enabled") { screen(enabled)() }

    @Test
    fun equalizerDisabled() = capture("equalizer_screen_disabled") {
        screen(enabled.copy(enabled = false))()
    }

    @Test
    fun equalizerPresetSheet() = capture("equalizer_screen_preset_sheet") {
        screen(enabled.copy(presetSheetVisible = true))()
    }

    @Test
    fun equalizerUnavailable() = capture("equalizer_screen_unavailable") {
        screen(EqualizerUiState())()
    }
}
