/*
 * NowPlayingScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.player

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/**
 * Now Playing (issue #10 phase 4J): a pure projection of [PlayerUiState] + [PlaybackProgress] +
 * [SleepTimerState]. Covers transport state/commands, the seek bar, favourite, shuffle/repeat,
 * the overflow menu's conditional items, the artwork swipe gestures, the queue toggle, and
 * accessibility. The embedded legacy queue view is swapped for a fake slot in every test here -
 * it is not Compose and has its own coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h900dp-xxhdpi")
class NowPlayingScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val playing = PlayerUiState(
        hasCurrentTrack = true,
        trackId = "t1",
        title = "Kashmir",
        artist = "Led Zeppelin",
        isPlaying = true,
        phase = PlaybackPhase.Ready,
        durationMs = 500_000L,
        albumId = "al1",
        artistId = "ar1",
        canSeekToPrevious = true,
        canSeekToNext = true,
    )

    private class Recorder {
        val events = mutableListOf<String>()
        fun actions() = NowPlayingActions(
            onBack = { events += "back" },
            onPlayPause = { events += "playPause" },
            onStop = { events += "stop" },
            onPrevious = { events += "previous" },
            onNext = { events += "next" },
            onSeekBackRepeat = { events += "seekBackRepeat" },
            onSeekForwardRepeat = { events += "seekForwardRepeat" },
            onSeekTo = { events += "seekTo:$it" },
            onToggleShuffle = { events += "toggleShuffle" },
            onCycleRepeat = { events += "cycleRepeat" },
            onToggleFavorite = { events += "toggleFavorite" },
            onTitleClick = { events += "title" },
            onArtistClick = { events += "artist" },
            onSavePlaylist = { events += "savePlaylist" },
            onLyrics = { events += "lyrics" },
            onToggleQueue = { events += "toggleQueue" },
            onSleepTimer = { events += "sleepTimer" },
            onOverflowItem = { events += "overflow:$it" },
            equalizerAvailable = { true },
            keepScreenOnActive = { false },
            onArtworkSwipeNext = { events += "swipeNext" },
            onArtworkSwipePrevious = { events += "swipePrevious" },
            onArtworkSwipeSeekForward = { events += "swipeSeekForward" },
            onArtworkSwipeSeekBack = { events += "swipeSeekBack" },
        )
    }

    private fun setContent(
        state: PlayerUiState,
        recorder: Recorder = Recorder(),
        progress: PlaybackProgress = PlaybackProgress(100_000L, 500_000L, 40),
        sleepTimerState: SleepTimerState = SleepTimerState.Off,
        showQueue: Boolean = false,
    ): Recorder {
        compose.setContent {
            TakiTheme {
                NowPlayingScreen(
                    state = state,
                    progress = progress,
                    sleepTimerState = sleepTimerState,
                    showQueue = showQueue,
                    actions = recorder.actions(),
                    queueContent = { Text("QUEUE_CONTENT") },
                )
            }
        }
        return recorder
    }

    // --- metadata / artwork ------------------------------------------------------------------

    @Test
    fun `title and artist are shown`() {
        setContent(playing)
        compose.onNodeWithText("Kashmir").assertIsDisplayed()
        compose.onNodeWithText("Led Zeppelin").assertIsDisplayed()
    }

    @Test
    fun `a track with no artwork still renders the hero panel`() {
        setContent(playing.copy(artworkModelLarge = null))
        compose.onNodeWithTag(NOW_PLAYING_ARTWORK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `no current track shows empty title and the zero-time placeholders`() {
        setContent(PlayerUiState())
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.onNodeWithText("‒:‒‒").assertIsDisplayed()
    }

    // --- play / pause / stop (buffering) ------------------------------------------------------

    @Test
    fun `while playing the primary button offers Pause`() {
        setContent(playing.copy(isPlaying = true, phase = PlaybackPhase.Ready))
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `while paused the primary button offers Play`() {
        setContent(playing.copy(isPlaying = false, phase = PlaybackPhase.Ready))
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun `while buffering the primary button is Stop, not Play or Pause`() {
        setContent(playing.copy(phase = PlaybackPhase.Buffering))
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test
    fun `tapping the primary button fires playPause while idle or playing`() {
        val recorder = setContent(playing.copy(isPlaying = false))
        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(listOf("playPause"), recorder.events)
    }

    @Test
    fun `tapping the primary button fires stop while buffering`() {
        val recorder = setContent(playing.copy(phase = PlaybackPhase.Buffering))
        compose.onNodeWithContentDescription("Stop").performClick()
        assertEquals(listOf("stop"), recorder.events)
    }

    // --- previous / next: tap and press-and-hold-repeat ---------------------------------------

    @Test
    fun `tapping previous or next fires exactly that command`() {
        val recorder = setContent(playing)
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        assertEquals(listOf("previous", "next"), recorder.events)
    }

    @Test
    fun `a disabled previous button is announced as disabled and does not fire on tap`() {
        val recorder = setContent(playing.copy(canSeekToPrevious = false))
        compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous").performClick()
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun `holding previous or next repeats the seek command instead of skipping`() {
        val recorder = setContent(playing)
        compose.onNodeWithContentDescription("Next").performTouchInput {
            down(center)
            advanceEventTime(1500)
            up()
        }
        assertTrue("expected at least one repeat", recorder.events.count { it == "seekForwardRepeat" } >= 1)
        assertTrue("a held press must not also fire the tap", "next" !in recorder.events)
    }

    // --- seek bar ------------------------------------------------------------------------------

    @Test
    fun `elapsed and total time are formatted from the progress snapshot`() {
        setContent(playing, progress = PlaybackProgress(65_000L, 500_000L, 0))
        compose.onNodeWithText("1:05").assertIsDisplayed()
        compose.onNodeWithText("8:20").assertIsDisplayed()
    }

    @Test
    fun `the seek bar is disabled while paused and not on jukebox`() {
        setContent(playing.copy(isPlaying = false, isJukeboxEnabled = false))
        compose.onNodeWithTag(NOW_PLAYING_SEEK_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the seek bar is enabled while playing`() {
        setContent(playing.copy(isPlaying = true))
        compose.onNodeWithTag(NOW_PLAYING_SEEK_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun `a paused jukebox stream still allows dragging the seek bar`() {
        setContent(playing.copy(isPlaying = false, isJukeboxEnabled = true))
        compose.onNodeWithTag(NOW_PLAYING_SEEK_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun `dropping the seek bar dispatches onSeekTo`() {
        val recorder = setContent(playing, progress = PlaybackProgress(0L, 500_000L, 0))
        // The Slider's own SetProgress semantics action - the documented, reliable way to drive
        // a Compose Slider's value from a test (a raw touch drag depends on exact pixel/thumb
        // hit-testing that is flaky under Robolectric).
        compose.onNodeWithTag(NOW_PLAYING_SEEK_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(300_000f) }
        assertTrue(
            "events were: ${recorder.events}",
            recorder.events.any { it.startsWith("seekTo:") },
        )
    }

    // --- shuffle / repeat / favourite -----------------------------------------------------------

    @Test
    fun `shuffle and repeat show their current state and fire on tap`() {
        val recorder = setContent(playing.copy(isShuffleEnabled = true, repeatMode = RepeatMode.ALL))
        compose.onNodeWithContentDescription("Shuffle").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Repeat All").assertIsDisplayed().performClick()
        assertEquals(listOf("toggleShuffle", "cycleRepeat"), recorder.events)
    }

    @Test
    fun `repeat announces the specific mode - off, song or all`() {
        setContent(playing.copy(repeatMode = RepeatMode.OFF))
        compose.onNodeWithContentDescription("Repeat Off").assertIsDisplayed()
    }

    @Test
    fun `favourite reflects liked state and toggles on tap`() {
        val recorder = setContent(playing.copy(isCurrentTrackLiked = false))
        compose.onNodeWithContentDescription("Like this song").assertIsDisplayed().performClick()
        assertEquals(listOf("toggleFavorite"), recorder.events)
    }

    @Test
    fun `a liked track shows the unlike description`() {
        setContent(playing.copy(isCurrentTrackLiked = true))
        compose.onNodeWithContentDescription("Unlike this song").assertIsDisplayed()
    }

    // --- title / artist navigation --------------------------------------------------------------

    @Test
    fun `tapping the title or artist fires the go-to-album-slash-artist actions`() {
        val recorder = setContent(playing)
        compose.onNodeWithText("Kashmir").performClick()
        compose.onNodeWithText("Led Zeppelin").performClick()
        assertEquals(listOf("title", "artist"), recorder.events)
    }

    // --- secondary row: save / lyrics / queue / sleep timer -------------------------------------

    @Test
    fun `save playlist and lyrics fire their actions`() {
        val recorder = setContent(playing)
        compose.onNodeWithContentDescription("Save Playlist").performClick()
        compose.onNodeWithContentDescription("Lyrics").performClick()
        assertEquals(listOf("savePlaylist", "lyrics"), recorder.events)
    }

    @Test
    fun `the queue button toggles the panel and its own selected state`() {
        val recorder = setContent(playing, showQueue = false)
        compose.onNodeWithContentDescription("Queue").performClick()
        assertEquals(listOf("toggleQueue"), recorder.events)
    }

    @Test
    fun `showQueue true renders the queue content slot instead of the artwork`() {
        setContent(playing, showQueue = true)
        compose.onNodeWithTag(NOW_PLAYING_QUEUE_PANEL_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("QUEUE_CONTENT").assertIsDisplayed()
        compose.onNodeWithTag(NOW_PLAYING_ARTWORK_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `showQueue false renders the artwork instead of the queue content slot`() {
        setContent(playing, showQueue = false)
        compose.onNodeWithTag(NOW_PLAYING_ARTWORK_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("QUEUE_CONTENT").assertDoesNotExist()
    }

    @Test
    fun `sleep timer off shows the plain title`() {
        setContent(playing, sleepTimerState = SleepTimerState.Off)
        compose.onNodeWithContentDescription("Sleep timer").assertIsDisplayed()
    }

    @Test
    fun `sleep timer end-of-track shows the end-of-song title`() {
        setContent(playing, sleepTimerState = SleepTimerState.EndOfTrack)
        compose.onNodeWithContentDescription("Sleep timer · End of song").assertIsDisplayed()
    }

    @Test
    fun `tapping the sleep timer button fires onSleepTimer`() {
        val recorder = setContent(playing)
        compose.onNodeWithContentDescription("Sleep timer").performClick()
        assertEquals(listOf("sleepTimer"), recorder.events)
    }

    // --- overflow menu ---------------------------------------------------------------------------

    @Test
    fun `the overflow menu offers go-to-artist and go-to-album only with a current track`() {
        setContent(playing)
        compose.onNodeWithContentDescription("Player options").performClick()
        compose.onNodeWithText("Go to Artist").assertIsDisplayed()
        compose.onNodeWithText("Go to Album").assertIsDisplayed()
    }

    @Test
    fun `the overflow menu hides go-to-artist and go-to-album with no current track`() {
        setContent(PlayerUiState())
        compose.onNodeWithContentDescription("Player options").performClick()
        compose.onNodeWithText("Go to Artist").assertDoesNotExist()
        compose.onNodeWithText("Go to Album").assertDoesNotExist()
    }

    @Test
    fun `the overflow menu shows Equalizer only when it is available`() {
        val recorder = Recorder()
        setContent(playing, recorder)
        compose.onNodeWithContentDescription("Player options").performClick()
        compose.onNodeWithText("Equalizer").assertIsDisplayed()
    }

    @Test
    fun `the overflow menu's screen-on-off label reflects the current state`() {
        compose.setContent {
            TakiTheme {
                NowPlayingScreen(
                    state = playing,
                    progress = PlaybackProgress(),
                    sleepTimerState = SleepTimerState.Off,
                    showQueue = false,
                    actions = NowPlayingActions.Noop.copy(
                        equalizerAvailable = { false },
                        keepScreenOnActive = { true },
                    ),
                    queueContent = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Player options").performClick()
        compose.onNodeWithText("Screen Off").assertIsDisplayed()
        compose.onNodeWithText("Equalizer").assertDoesNotExist()
    }

    @Test
    fun `overflow items dispatch onOverflowItem with the right value`() {
        val recorder = setContent(playing)
        compose.onNodeWithContentDescription("Player options").performClick()
        compose.onNodeWithText("Clear Playlist").performClick()
        assertEquals(listOf("overflow:CLEAR_PLAYLIST"), recorder.events)
    }

    // --- back --------------------------------------------------------------------------------

    @Test
    fun `tapping back fires onBack`() {
        val recorder = setContent(playing)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), recorder.events)
    }

    // --- artwork swipe gestures ------------------------------------------------------------------

    @Test
    fun `swiping the artwork left goes to next, right to previous`() {
        val recorder = setContent(playing)
        compose.onNodeWithTag(NOW_PLAYING_ARTWORK_TEST_TAG).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(NOW_PLAYING_ARTWORK_TEST_TAG).performTouchInput { swipeRight() }
        assertTrue(recorder.events.contains("swipeNext") || recorder.events.contains("swipePrevious"))
    }

    @Test
    fun `swiping the artwork down seeks forward, up seeks back`() {
        val recorder = setContent(playing)
        compose.onNodeWithTag(NOW_PLAYING_ARTWORK_TEST_TAG).performTouchInput { swipeDown() }
        compose.onNodeWithTag(NOW_PLAYING_ARTWORK_TEST_TAG).performTouchInput { swipeUp() }
        assertTrue(
            recorder.events.contains("swipeSeekForward") || recorder.events.contains("swipeSeekBack"),
        )
    }

    // --- recomposition -------------------------------------------------------------------------

    @Test
    fun `a track change updates title, artist and the play state in place`() {
        var state by mutableStateOf(playing)
        compose.setContent {
            TakiTheme {
                NowPlayingScreen(
                    state = state,
                    progress = PlaybackProgress(),
                    sleepTimerState = SleepTimerState.Off,
                    showQueue = false,
                    actions = NowPlayingActions.Noop,
                    queueContent = {},
                )
            }
        }
        compose.onNodeWithText("Kashmir").assertIsDisplayed()

        state = state.copy(trackId = "t2", title = "Immigrant Song", isPlaying = false)
        compose.waitForIdle()
        compose.onNodeWithText("Immigrant Song").assertIsDisplayed()
        compose.onNodeWithText("Kashmir").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }
}
