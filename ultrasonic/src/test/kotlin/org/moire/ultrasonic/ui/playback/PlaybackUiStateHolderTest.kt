/*
 * PlaybackUiStateHolderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import app.cash.turbine.test
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
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
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

    @Test
    fun `projects the current track into a Media3-free ui state`() = runTest {
        val holder = PlaybackUiStateHolder(mediaPlayerManager, backgroundScope, artworkResolver = { null })

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
        val holder = PlaybackUiStateHolder(mediaPlayerManager, backgroundScope, artworkResolver = { null })

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
        val holder = PlaybackUiStateHolder(mediaPlayerManager)

        holder.onPlayPause()
        holder.onNext()
        holder.onPrevious()

        verify(mediaPlayerManager).togglePlayPause()
        verify(mediaPlayerManager).seekToNext()
        verify(mediaPlayerManager).seekToPrevious()
    }

    @Test
    fun `does not touch MediaPlayerManager just by existing`() {
        PlaybackUiStateHolder(mediaPlayerManager)
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
        val holder = PlaybackUiStateHolder(
            mediaPlayerManager,
            backgroundScope,
            artworkResolver = { CoverArtRequest(it.coverArt.orEmpty(), "key-${it.id}", 0) },
        )

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
        val holder = PlaybackUiStateHolder(
            mediaPlayerManager,
            backgroundScope,
            artworkResolver = { CoverArtRequest(it.id, "k-${it.id}", 0) },
        )

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
        val holder = PlaybackUiStateHolder(mediaPlayerManager, backgroundScope, artworkResolver = { null })

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
        val holder = PlaybackUiStateHolder(mediaPlayerManager)
        assertEquals(PlaybackProgress(30_000L, 120_000L), holder.snapshotProgress())

        whenever(mediaPlayerManager.playerPosition).thenReturn(-1)
        whenever(mediaPlayerManager.playerDuration).thenReturn(-1)
        assertEquals(PlaybackProgress(0L, 0L), holder.snapshotProgress())
    }
}
