/*
 * TakiMiniPlayerComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Compose mini-player (issue #10 phase 4I) as a pure projection of [PlayerUiState]: it
 * renders nothing without a track, shows title / artist / play-pause state, fires the same five
 * commands the legacy `NowPlayingFragment` did, keeps the legacy swipe / tap rules, and exposes
 * sensible semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h800dp-xxhdpi")
class TakiMiniPlayerComposeTest {

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

    private class Recorder {
        val events = mutableListOf<String>()
        val actions = MiniPlayerActions(
            onOpenNowPlaying = { events += "open" },
            onArtworkClick = { events += "artwork" },
            onPlayPause = { events += "playPause" },
            onPrevious = { events += "previous" },
            onNext = { events += "next" },
        )
    }

    private fun setContent(
        state: PlayerUiState,
        actions: MiniPlayerActions = MiniPlayerActions.Noop,
        progress: PlaybackProgress = PlaybackProgress(50_000L, 200_000L),
    ) {
        compose.setContent {
            TakiTheme { TakiMiniPlayer(state = state, actions = actions, progressProvider = { progress }) }
        }
    }

    // --- visibility ------------------------------------------------------------------------

    @Test
    fun `without a current track nothing is rendered`() {
        setContent(PlayerUiState())
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `with a current track the bar is shown`() {
        setContent(playing)
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).assertIsDisplayed()
    }

    // --- content ---------------------------------------------------------------------------

    @Test
    fun `title and artist are shown`() {
        setContent(playing)
        compose.onNodeWithText("Shine a Little Light").assertIsDisplayed()
        compose.onNodeWithText("The Black Keys").assertIsDisplayed()
    }

    @Test
    fun `a track without an artist shows just the title`() {
        setContent(playing.copy(artist = null))
        compose.onNodeWithText("Shine a Little Light").assertIsDisplayed()
        compose.onNodeWithText("The Black Keys").assertDoesNotExist()
    }

    @Test
    fun `a track with no artwork still renders the bar (neutral placeholder)`() {
        setContent(playing.copy(artworkModel = null))
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).assertIsDisplayed()
    }

    // --- play / pause state ----------------------------------------------------------------

    @Test
    fun `while playing the transport offers Pause`() {
        setContent(playing.copy(isPlaying = true))
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun `while paused the transport offers Play`() {
        setContent(playing.copy(isPlaying = false))
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test
    fun `buffering keeps the bar and shows the not-playing icon`() {
        setContent(playing.copy(isPlaying = false, phase = PlaybackPhase.Buffering))
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    // --- commands --------------------------------------------------------------------------

    @Test
    fun `play pause previous and next each fire exactly their own command`() {
        val recorder = Recorder()
        setContent(playing, recorder.actions)

        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Next").performClick()

        assertEquals(listOf("playPause", "previous", "next"), recorder.events)
    }

    @Test
    fun `tapping the cover opens the album and nothing else`() {
        val recorder = Recorder()
        setContent(playing, recorder.actions)
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).performTouchInput { click(centerLeft.copy(x = 60f)) }
        assertEquals(listOf("artwork"), recorder.events)
    }

    @Test
    fun `tapping the info column opens Now Playing`() {
        val recorder = Recorder()
        setContent(playing, recorder.actions)
        compose.onNodeWithText("Shine a Little Light").performClick()
        assertEquals(listOf("open"), recorder.events)
    }

    @Test
    fun `swiping right goes to the previous track, swiping left to the next`() {
        val recorder = Recorder()
        setContent(playing, recorder.actions)

        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).performTouchInput { swipeRight() }
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).performTouchInput { swipeLeft() }

        assertEquals(listOf("previous", "next"), recorder.events)
    }

    // --- accessibility ---------------------------------------------------------------------

    @Test
    fun `the info column is one control announcing title and artist with a Now Playing action`() {
        val recorder = Recorder()
        setContent(playing, recorder.actions)

        val info = compose.onNodeWithText("Shine a Little Light", useUnmergedTree = false)
        info.assertIsDisplayed()
        info.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("open"), recorder.events)
    }

    @Test
    fun `the cover is decorative - it is not announced`() {
        setContent(playing)
        compose.onNodeWithContentDescription("Album artwork").assertDoesNotExist()
    }

    // --- progress line ---------------------------------------------------------------------

    @Test
    fun `the progress line shows once a duration is known`() {
        setContent(playing, progress = PlaybackProgress(50_000L, 200_000L))
        compose.waitForIdle()
        compose.onNodeWithTag(MINI_PLAYER_PROGRESS_TEST_TAG).assertExists()
    }

    @Test
    fun `no duration means no progress line`() {
        setContent(playing, progress = PlaybackProgress(0L, 0L))
        compose.waitForIdle()
        compose.onNodeWithTag(MINI_PLAYER_PROGRESS_TEST_TAG).assertDoesNotExist()
    }

    // --- recomposition ---------------------------------------------------------------------

    @Test
    fun `a track change and a pause update the same bar in place`() {
        var state by mutableStateOf(playing)
        compose.setContent {
            TakiTheme {
                TakiMiniPlayer(
                    state = state,
                    actions = MiniPlayerActions.Noop,
                    progressProvider = { PlaybackProgress(0L, 100_000L) },
                )
            }
        }
        compose.onNodeWithText("Shine a Little Light").assertIsDisplayed()

        state = state.copy(trackId = "t2", title = "Eagle Birds", artist = "The Black Keys")
        compose.waitForIdle()
        compose.onNodeWithText("Eagle Birds").assertIsDisplayed()
        compose.onNodeWithText("Shine a Little Light").assertDoesNotExist()

        state = state.copy(isPlaying = false)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun `clearing the track hides the bar again`() {
        var state by mutableStateOf(playing)
        compose.setContent {
            TakiTheme {
                TakiMiniPlayer(state = state, actions = MiniPlayerActions.Noop, progressProvider = { PlaybackProgress() })
            }
        }
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).assertIsDisplayed()

        state = PlayerUiState()
        compose.waitForIdle()
        compose.onNodeWithTag(MINI_PLAYER_TEST_TAG).assertDoesNotExist()
    }
}
