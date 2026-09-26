/*
 * NowPlayingScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

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
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.ui.playback.PlaybackPhase
import org.moire.ultrasonic.ui.playback.PlaybackProgress
import org.moire.ultrasonic.ui.playback.PlayerUiState
import org.moire.ultrasonic.ui.playback.RepeatMode
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Now Playing (issue #10 phase 4J): the standard playing state, paused,
 * and buffering (the Stop primary button). Fully deterministic - fake [PlayerUiState], no
 * artwork network (the atmosphere and hero both fall back to the neutral placeholder with a
 * null model). Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h900dp-xxhdpi")
class NowPlayingScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val playing = PlayerUiState(
        hasCurrentTrack = true,
        trackId = "t1",
        title = "Paranoid Android",
        artist = "Radiohead",
        isPlaying = true,
        phase = PlaybackPhase.Ready,
        durationMs = 383_000L,
        isShuffleEnabled = true,
        repeatMode = RepeatMode.ALL,
        canSeekToPrevious = true,
        canSeekToNext = true,
    )

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(412.dp)
                        .heightIn(max = 900.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: PlayerUiState, progress: PlaybackProgress): @Composable () -> Unit = {
        NowPlayingScreen(
            state = state,
            progress = progress,
            sleepTimerState = SleepTimerState.Off,
            showQueue = false,
            actions = NowPlayingActions.Noop,
            queueContent = {},
            upNext = listOf(UpNextItem("Karma Police", "Radiohead", null, 1)),
        )
    }

    @Test
    fun nowPlayingStandard() =
        capture("now_playing_standard", screen(playing, PlaybackProgress(140_000L, 383_000L, 55)))

    @Test
    fun nowPlayingPaused() = capture(
        "now_playing_paused",
        screen(playing.copy(isPlaying = false), PlaybackProgress(200_000L, 383_000L, 100)),
    )

    @Test
    fun nowPlayingBuffering() = capture(
        "now_playing_buffering",
        screen(
            playing.copy(isPlaying = false, phase = PlaybackPhase.Buffering),
            PlaybackProgress(10_000L, 383_000L, 5),
        ),
    )
}
