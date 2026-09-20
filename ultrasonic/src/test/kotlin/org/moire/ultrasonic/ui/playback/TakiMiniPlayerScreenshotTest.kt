/*
 * TakiMiniPlayerScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
 * Roborazzi goldens for the mini-player (issue #10 phase 4I): playing (with its progress line)
 * and paused with a long, marquee-length title. Deterministic - a fixed progress provider, no
 * artwork network (the neutral placeholder). Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h200dp-xxhdpi")
class TakiMiniPlayerScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val playing = PlayerUiState(
        hasCurrentTrack = true,
        trackId = "t1",
        title = "Shine a Little Light",
        artist = "The Black Keys",
        isPlaying = true,
        phase = PlaybackPhase.Ready,
        durationMs = 200_000L,
    )

    private fun capture(tag: String, state: PlayerUiState, progress: PlaybackProgress) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .background(TakiTheme.colors.black)
                        .padding(16.dp),
                ) {
                    Frame(state, progress)
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Composable
    private fun Frame(state: PlayerUiState, progress: PlaybackProgress) {
        TakiMiniPlayer(state = state, actions = MiniPlayerActions.Noop, progressProvider = { progress })
    }

    @Test
    fun miniPlayerPlaying() =
        capture("mini_player_playing", playing, PlaybackProgress(70_000L, 200_000L))

    @Test
    fun miniPlayerPausedLongTitle() = capture(
        "mini_player_paused",
        playing.copy(
            isPlaying = false,
            title = "A Very Long Track Title That Will Not Fit In The Available Width",
        ),
        PlaybackProgress(150_000L, 200_000L),
    )
}
