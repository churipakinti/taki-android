/*
 * PlaybackRestorePositionTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Playback resumption position (issue #43 follow-up to AA-07). After process death the saved queue is
 * restored into the player and Media3's playback resumption reads index and position back from it.
 * Device logs showed the position *is* restored; they also showed that a checkpoint with index 0 /
 * position 0 was written while the restore was in flight (the queue had landed, the controller had
 * not yet applied the seek), which would have replaced the good saved checkpoint had the process died
 * in that window. These tests pin both: the restored state and that no such interim checkpoint is
 * written.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlaybackRestorePositionTest {

    private val serializer: PlaybackStateSerializer = mock()
    private lateinit var manager: MediaPlayerManager
    private lateinit var player: FakeMedia3Player

    private val queue = listOf("A", "B", "C", "D").map { Track(id = it, title = "Title $it") }

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        queue.forEach {
            MediaItemConverter.addToCache(
                it.id,
                buildMediaItem(title = it.title ?: it.id, mediaId = it.id, isPlayable = true)
            )
        }
        manager = MediaPlayerManager(serializer, mock())
        player = FakeMedia3Player(Looper.getMainLooper())
        manager.setPrivateField("controller", player)
    }

    @After
    fun tearDown() {
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun state(index: Int, position: Int) =
        PlaybackState(queue, index, position, false, Player.REPEAT_MODE_OFF)

    private fun pump() = shadowOf(Looper.getMainLooper()).idle()

    // restore() rebuilds the queue in an asynchronous coroutine; wait until it has landed and the seek ran.
    private fun restoreAndWait(state: PlaybackState, autoPlay: Boolean) {
        manager.restore(state, autoPlay)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            pump()
            if (player.mediaItemCount == queue.size && player.currentMediaItemIndex == state.currentPlayingIndex) break
            Thread.sleep(5)
        }
        pump()
    }

    private fun triggerQueueCheckpoint() {
        val method = MediaPlayerManager::class.java.getDeclaredMethod("serializeCurrentSession")
        method.isAccessible = true
        method.invoke(manager)
    }

    @Test
    fun `restore does not write an index 0 position 0 checkpoint before the seek lands`() {
        // The controller applies the seek asynchronously: a queue event that arrives first sees
        // index 0 / position 0 and must not overwrite the saved checkpoint with it.
        player.beforeSeek = { triggerQueueCheckpoint() }

        restoreAndWait(state(index = 2, position = 91_000), autoPlay = false)

        val index = argumentCaptor<Int>()
        val position = argumentCaptor<Int>()
        verify(serializer, atLeast(0)).serializeAsync(any(), index.capture(), position.capture(), any(), any())
        val interim = index.allValues.zip(position.allValues).filter { it == 0 to 0 }
        assertEquals("no interim 0/0 checkpoint while restoring", emptyList<Pair<Int, Int>>(), interim)
    }

    @Test
    fun `checkpoints are written again once the restore has finished`() {
        restoreAndWait(state(index = 2, position = 91_000), autoPlay = false)

        triggerQueueCheckpoint()

        val index = argumentCaptor<Int>()
        val position = argumentCaptor<Int>()
        verify(serializer, atLeast(1)).serializeAsync(any(), index.capture(), position.capture(), any(), any())
        assertEquals(2 to 91_000, index.lastValue to position.lastValue)
    }

    @Test
    fun `media3 resumption after a restore returns the saved item and position`() {
        restoreAndWait(state(index = 2, position = 91_000), autoPlay = false)

        val resumption = resumptionFromPlayer(player)
        assertNotNull(resumption)
        assertEquals(listOf("A", "B", "C", "D"), resumption!!.mediaItems.map { it.mediaId })
        assertEquals(2, resumption.startIndex)
        assertEquals(91_000L, resumption.startPositionMs)
    }

    @Test
    fun `a restore without autoplay leaves a paused session paused`() {
        restoreAndWait(state(index = 1, position = 12_000), autoPlay = false)
        assertFalse(player.playWhenReady)
        assertEquals(12_000L, player.currentPosition)
    }

    @Test
    fun `a restore with autoplay starts at the saved position`() {
        restoreAndWait(state(index = 3, position = 5_000), autoPlay = true)
        assertEquals(3, player.currentMediaItemIndex)
        assertEquals(5_000L, player.currentPosition)
        assertEquals(true, player.playWhenReady)
    }

    @Test
    fun `a seek that is already queued behind a newer one is not replayed over a user action`() {
        restoreAndWait(state(index = 1, position = 8_000), autoPlay = false)
        manager.seekTo(3, 1_000) // the user moves on after the restore
        pump()
        assertEquals(3, player.currentMediaItemIndex)
        assertEquals(1_000L, player.currentPosition)
    }
}
