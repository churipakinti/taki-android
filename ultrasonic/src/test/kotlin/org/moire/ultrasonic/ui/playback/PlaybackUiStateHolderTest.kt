/*
 * PlaybackUiStateHolderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.SleepTimerState
import org.robolectric.RobolectricTestRunner

/**
 * Contract for [PlaybackUiStateHolder]: it is a one-way projection of RxBus playback state
 * into a Media3-free [PlayerUiState], and it forwards commands to [MediaPlayerManager]
 * without touching its own state.
 *
 * Robolectric is needed only because the `RxBus` companion object wires some observables
 * onto `AndroidSchedulers.mainThread()` on class load.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackUiStateHolderTest {

    private val mediaPlayerManager: MediaPlayerManager = mock()

    private fun track(id: String) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        coverArt = "cover-$id",
        starred = true,
        duration = 200
    )

    /** For tests that only call a command method synchronously - no coroutine ever runs. */
    private fun holder(
        artworkResolver: (Track) -> CoverArtRequest? = { null },
        largeArtworkResolver: (Track) -> CoverArtRequest? = { null },
    ) = PlaybackUiStateHolder(
        mediaPlayerManager,
        artworkResolver = artworkResolver,
        largeArtworkResolver = largeArtworkResolver,
    )

    /** For tests that drive [PlaybackUiStateHolder.playerState] and need it tied to the test's
     *  virtual clock (`backgroundScope`, from the `runTest` receiver). */
    private fun TestScope.testHolder(
        artworkResolver: (Track) -> CoverArtRequest? = { null },
        largeArtworkResolver: (Track) -> CoverArtRequest? = { null },
    ) = PlaybackUiStateHolder(
        mediaPlayerManager,
        backgroundScope,
        artworkResolver = artworkResolver,
        largeArtworkResolver = largeArtworkResolver,
    )

    @Test
    fun `projects the current track into a Media3-free ui state`() = runTest {
        val holder = testHolder()

        holder.playerState.test {
            assertEquals(PlayerUiState(), awaitItem())

            RxBus.playerStatePublisher.onNext(
                RxBus.StateWithTrack(
                    track = track("t1"),
                    index = 0,
                    isPlaying = true,
                    // androidx.media3.common.Player.STATE_READY
                    state = 3
                )
            )

            var state = awaitItem()
            while (state.trackId != "t1") state = awaitItem()

            assertEquals("Title t1", state.title)
            assertEquals("Artist t1", state.artist)
            assertEquals("cover-t1", state.coverArtId)
            assertTrue(state.hasCurrentTrack)
            assertTrue(state.isPlaying)
            assertEquals(PlaybackPhase.Ready, state.phase)
            assertTrue(state.isCurrentTrackLiked)
            assertEquals(200_000L, state.durationMs)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `maps an empty player state to no current track`() = runTest {
        val holder = testHolder()

        holder.playerState.test {
            skipItems(1) // initial

            // Move off the initial value first so the transition back is observable
            // regardless of what a previous test left in the RxBus replay cache.
            RxBus.playerStatePublisher.onNext(
                RxBus.StateWithTrack(track = track("seed"), index = 0, isPlaying = true, state = 3)
            )
            var state = awaitItem()
            while (state.trackId != "seed") state = awaitItem()

            RxBus.playerStatePublisher.onNext(
                // androidx.media3.common.Player.STATE_IDLE
                RxBus.StateWithTrack(track = null, index = -1, isPlaying = false, state = 1)
            )
            state = awaitItem()
            while (state.hasCurrentTrack) state = awaitItem()

            assertFalse(state.hasCurrentTrack)
            assertFalse(state.isPlaying)
            assertEquals(PlaybackPhase.Idle, state.phase)
            assertEquals(PlayerUiState(), state)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forwards commands to MediaPlayerManager and keeps no state of its own`() {
        val holder = holder()

        holder.onPlayPause()
        holder.onNext()
        holder.onPrevious()

        verify(mediaPlayerManager).togglePlayPause()
        verify(mediaPlayerManager).seekToNext()
        verify(mediaPlayerManager).seekToPrevious()
    }

    @Test
    fun `does not touch MediaPlayerManager just by existing`() {
        holder()
        verifyNoInteractions(mediaPlayerManager)
    }

    @Test
    fun `maps the Media3 state ints without importing Media3`() {
        assertEquals(PlaybackPhase.Idle, PlaybackPhase.fromMedia3State(1))
        assertEquals(PlaybackPhase.Buffering, PlaybackPhase.fromMedia3State(2))
        assertEquals(PlaybackPhase.Ready, PlaybackPhase.fromMedia3State(3))
        assertEquals(PlaybackPhase.Ended, PlaybackPhase.fromMedia3State(4))
        assertEquals(PlaybackPhase.Idle, PlaybackPhase.fromMedia3State(99))
    }

    // --- Mini-player projection (issue #10 phase 4I) -------------------------------------------

    @Test
    fun `projects the resolved cover artwork model for the current track`() = runTest {
        val holder = testHolder(artworkResolver = { CoverArtRequest(it.coverArt.orEmpty(), "key-${it.id}", 0) })

        holder.playerState.test {
            skipItems(1)
            RxBus.playerStatePublisher.onNext(
                RxBus.StateWithTrack(track = track("art"), index = 0, isPlaying = true, state = 3)
            )
            var state = awaitItem()
            while (state.trackId != "art") state = awaitItem()

            assertEquals(CoverArtRequest("cover-art", "key-art", 0), state.artworkModel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a track change and a pause each project into the next state`() = runTest {
        val holder = testHolder(artworkResolver = { CoverArtRequest(it.id, "k-${it.id}", 0) })

        holder.playerState.test {
            skipItems(1)
            RxBus.playerStatePublisher.onNext(
                RxBus.StateWithTrack(track = track("a"), index = 0, isPlaying = true, state = 3)
            )
            var state = awaitItem()
            while (state.trackId != "a") state = awaitItem()
            assertTrue(state.isPlaying)

            RxBus.playerStatePublisher.onNext(
                RxBus.StateWithTrack(track = track("b"), index = 1, isPlaying = true, state = 3)
            )
            state = awaitItem()
            while (state.trackId != "b") state = awaitItem()
            assertEquals("Title b", state.title)
            assertEquals("k-b", state.artworkModel?.cacheKey)

            RxBus.playerStatePublisher.onNext(
                RxBus.StateWithTrack(track = track("b"), index = 1, isPlaying = false, state = 3)
            )
            state = awaitItem()
            while (state.isPlaying) state = awaitItem()
            assertEquals("b", state.trackId)
            assertFalse(state.isPlaying)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a buffering track still counts as current, with the buffering phase`() = runTest {
        val holder = testHolder()

        holder.playerState.test {
            skipItems(1)
            RxBus.playerStatePublisher.onNext(
                // androidx.media3.common.Player.STATE_BUFFERING
                RxBus.StateWithTrack(track = track("buf"), index = 0, isPlaying = false, state = 2)
            )
            var state = awaitItem()
            while (state.trackId != "buf") state = awaitItem()
            assertTrue(state.hasCurrentTrack)
            assertEquals(PlaybackPhase.Buffering, state.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snapshotProgress reads the transport and never goes negative`() {
        whenever(mediaPlayerManager.playerPosition).thenReturn(30_000)
        whenever(mediaPlayerManager.playerDuration).thenReturn(120_000)
        whenever(mediaPlayerManager.bufferedPercentage).thenReturn(75)
        val holder = holder()
        assertEquals(PlaybackProgress(30_000L, 120_000L, 75), holder.snapshotProgress())

        whenever(mediaPlayerManager.playerPosition).thenReturn(-1)
        whenever(mediaPlayerManager.playerDuration).thenReturn(-1)
        whenever(mediaPlayerManager.bufferedPercentage).thenReturn(0)
        assertEquals(PlaybackProgress(0L, 0L, 0), holder.snapshotProgress())
    }

    // --- Now Playing projection (issue #10 phase 4J) --------------------------------------------

    private fun track(
        id: String,
        albumId: String? = "al-$id",
        artistId: String? = "ar-$id",
        parent: String? = "parent-$id",
    ) = Track(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        albumId = albumId,
        artistId = artistId,
        parent = parent,
        coverArt = "cover-$id",
        duration = 200,
    )

    private fun publish(index: Int = 0, isPlaying: Boolean = true, track: Track = track("t")) {
        RxBus.playerStatePublisher.onNext(
            RxBus.StateWithTrack(track = track, index = index, isPlaying = isPlaying, state = 3),
        )
    }

    @Test
    fun `projects the large hero artwork model separately from the small one`() = runTest {
        val holder = testHolder(
            artworkResolver = { CoverArtRequest(it.id, "small-${it.id}", 0) },
            largeArtworkResolver = { CoverArtRequest(it.id, "large-${it.id}", 0) },
        )

        holder.playerState.test {
            skipItems(1)
            publish(track = track("hero"))
            var state = awaitItem()
            while (state.trackId != "hero") state = awaitItem()

            assertEquals("small-hero", state.artworkModel?.cacheKey)
            assertEquals("large-hero", state.artworkModelLarge?.cacheKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `projects albumId, artistId and parentId for the go-to-album-slash-artist navigation`() =
        runTest {
            val holder = testHolder()
            holder.playerState.test {
                skipItems(1)
                publish(track = track("nav", albumId = "AL1", artistId = "AR1", parent = "P1"))
                var state = awaitItem()
                while (state.trackId != "nav") state = awaitItem()

                assertEquals("AL1", state.albumId)
                assertEquals("AR1", state.artistId)
                assertEquals("P1", state.parentId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `projects the shuffle-aware play-order index verbatim from the RxBus event`() = runTest {
        val holder = testHolder()
        holder.playerState.test {
            skipItems(1)
            publish(index = 7, track = track("idx"))
            var state = awaitItem()
            while (state.trackId != "idx") state = awaitItem()
            assertEquals(7, state.currentIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reads shuffle, repeat, seek-availability and jukebox from MediaPlayerManager at map time`() =
        runTest {
            whenever(mediaPlayerManager.isShufflePlayEnabled).thenReturn(true)
            whenever(mediaPlayerManager.repeatMode).thenReturn(2)
            whenever(mediaPlayerManager.canSeekToPrevious()).thenReturn(false)
            whenever(mediaPlayerManager.canSeekToNext()).thenReturn(true)
            whenever(mediaPlayerManager.isJukeboxEnabled).thenReturn(true)
            val holder = testHolder()

            holder.playerState.test {
                skipItems(1)
                publish(track = track("modes"))
                var state = awaitItem()
                while (state.trackId != "modes") state = awaitItem()

                assertTrue(state.isShuffleEnabled)
                assertEquals(RepeatMode.ALL, state.repeatMode)
                assertFalse(state.canSeekToPrevious)
                assertTrue(state.canSeekToNext)
                assertTrue(state.isJukeboxEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RepeatMode int mapping matches Media3's REPEAT_MODE constants`() {
        assertEquals(RepeatMode.OFF, RepeatMode.fromInt(0))
        assertEquals(RepeatMode.ONE, RepeatMode.fromInt(1))
        assertEquals(RepeatMode.ALL, RepeatMode.fromInt(2))
        assertEquals(RepeatMode.OFF, RepeatMode.fromInt(99))
        assertEquals(0, RepeatMode.OFF.toInt())
        assertEquals(1, RepeatMode.ONE.toInt())
        assertEquals(2, RepeatMode.ALL.toInt())
    }

    @Test
    fun `RepeatMode cycles Off to Song to All to Off, the legacy repeat button order`() {
        assertEquals(RepeatMode.ONE, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ALL, RepeatMode.ONE.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ALL.next())
    }

    @Test
    fun `onStop resets playback, the legacy buffering Stop button`() {
        holder().onStop()
        verify(mediaPlayerManager).reset()
    }

    @Test
    fun `onSeekTo, onSeekBack and onSeekForward forward verbatim`() {
        val h = holder()
        h.onSeekTo(12_345)
        h.onSeekBack()
        h.onSeekForward()
        verify(mediaPlayerManager).seekTo(12_345)
        verify(mediaPlayerManager).seekBack()
        verify(mediaPlayerManager).seekForward()
    }

    @Test
    fun `onToggleShuffle forwards and returns the new state`() {
        whenever(mediaPlayerManager.toggleShuffle()).thenReturn(true)
        assertTrue(holder().onToggleShuffle())
        verify(mediaPlayerManager).toggleShuffle()
    }

    @Test
    fun `onCycleRepeat sets and returns the next mode, reading the current mode fresh`() {
        whenever(mediaPlayerManager.repeatMode).thenReturn(0)
        val next = holder().onCycleRepeat()
        assertEquals(RepeatMode.ONE, next)
        verify(mediaPlayerManager).repeatMode = 1
    }

    @Test
    fun `sleep timer commands forward verbatim`() {
        val h = holder()
        h.onSetSleepTimer(30)
        h.onSetSleepTimerEndOfTrack()
        h.onCancelSleepTimer()
        verify(mediaPlayerManager).setSleepTimer(30)
        verify(mediaPlayerManager).setSleepTimerEndOfTrack()
        verify(mediaPlayerManager).cancelSleepTimer()
    }

    @Test
    fun `onClearQueue turns shuffle off first, then clears`() {
        holder().onClearQueue()
        val order = org.mockito.kotlin.inOrder(mediaPlayerManager)
        order.verify(mediaPlayerManager).isShufflePlayEnabled = false
        order.verify(mediaPlayerManager).clear()
    }

    @Test
    fun `sleep timer state starts Off and projects an armed duration timer`() = runTest {
        val holder = PlaybackUiStateHolder(
            mediaPlayerManager,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        holder.sleepTimerState.test {
            assertEquals(SleepTimerState.Off, awaitItem())

            val armed = SleepTimerState.Duration(deadlineElapsedRealtime = 60_000L, presetMinutes = 15)
            RxBus.sleepTimerStatePublisher.onNext(armed)
            assertEquals(armed, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
