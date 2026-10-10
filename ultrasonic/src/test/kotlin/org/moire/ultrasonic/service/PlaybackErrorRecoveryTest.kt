/*
 * PlaybackErrorRecoveryTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner

/**
 * #30: user feedback for a playback source error, and Next/skip recovery from it.
 *
 * Media3 has no STATE_ERROR: after a fatal source error the player is `STATE_IDLE` with
 * `playerError != null` and keeps `playWhenReady`. A skip then only moves the index; nothing is
 * loaded until `prepare()`. These tests drive the REAL listener of [MediaPlayerManager] (the one
 * wired to the MediaController, so it also sees notification / headset Next) with a mocked player.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackErrorRecoveryTest {

    private lateinit var manager: MediaPlayerManager
    private lateinit var listener: Player.Listener
    private lateinit var player: Player
    private val messages = mutableListOf<Int>()

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        manager = MediaPlayerManager(mock(), mock())
        player = mock()
        manager.setPrivateField("controller", player)
        manager.playbackErrorSink = { messages += it }
        listener = manager.getPrivateField("listeners") as Player.Listener
        whenever(player.playWhenReady).thenReturn(true)
        whenever(player.currentMediaItem).thenReturn(item("A"))
    }

    private fun item(id: String): MediaItem = buildMediaItem(title = id, mediaId = id, isPlayable = true)

    private fun error(code: Int) = PlaybackException("test", null, code)

    private fun failed() {
        whenever(player.playerError)
            .thenReturn(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        whenever(player.playbackState).thenReturn(Player.STATE_IDLE)
    }

    private fun healthy() {
        whenever(player.playerError).thenReturn(null)
        whenever(player.playbackState).thenReturn(Player.STATE_READY)
    }

    // --- Classification -------------------------------------------------------------------------

    @Test
    fun `error codes are classified into the three treatments`() {
        assertEquals(
            PlaybackErrorKind.TRACK_UNAVAILABLE,
            classifyPlaybackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        )
        assertEquals(
            PlaybackErrorKind.TRACK_UNAVAILABLE,
            classifyPlaybackError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        )
        assertEquals(
            PlaybackErrorKind.TRANSIENT_NETWORK,
            classifyPlaybackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        )
        assertEquals(
            PlaybackErrorKind.GENERIC,
            classifyPlaybackError(PlaybackException.ERROR_CODE_DECODING_FAILED)
        )
    }

    // --- Feedback ---------------------------------------------------------------------------------

    @Test
    fun `an unrecognised stream gives exactly one track-unavailable message`() {
        listener.onPlayerError(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertEquals(listOf(R.string.download_play_error_track_unavailable), messages)
    }

    @Test
    fun `a generic decoder failure gives exactly one generic message`() {
        listener.onPlayerError(error(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals(listOf(R.string.download_play_error), messages)
    }

    @Test
    fun `adjacent duplicate callbacks for the same failed item do not repeat the message`() {
        val e = error(PlaybackException.ERROR_CODE_DECODING_FAILED)
        listener.onPlayerError(e)
        listener.onPlayerError(e)
        assertEquals(1, messages.size)
    }

    @Test
    fun `retrying the same item after it left the failed state reports a new failure`() {
        val e = error(PlaybackException.ERROR_CODE_DECODING_FAILED)
        listener.onPlayerError(e)
        listener.onPlaybackStateChanged(Player.STATE_BUFFERING) // the user pressed Play
        listener.onPlayerError(e)
        assertEquals(2, messages.size)
    }

    @Test
    fun `a transient network error with retries left shows no message yet`() {
        listener.onPlayerError(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a transient network error with the retry budget spent shows the final message once`() {
        manager.setPrivateField("networkErrorRetryCount", 3)
        listener.onPlayerError(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(listOf(R.string.download_play_error), messages)
    }

    // --- Next recovery ----------------------------------------------------------------------------

    @Test
    fun `Next away from a failed item prepares the new item once without play or stop`() {
        failed()
        listener.onMediaItemTransition(item("B"), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        verify(player, times(1)).prepare()
        verify(player, never()).play()
        verify(player, never()).stop()
        verify(player, never()).clearMediaItems()
    }

    @Test
    fun `healthy Next does not prepare`() {
        healthy()
        listener.onMediaItemTransition(item("B"), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        verify(player, never()).prepare()
    }

    @Test
    fun `automatic and repeat transitions never trigger the recovery`() {
        failed()
        listener.onMediaItemTransition(item("B"), Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        listener.onMediaItemTransition(item("A"), Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        verify(player, never()).prepare()
    }

    @Test
    fun `errored last item - Next raises no transition so no prepare and no second message`() {
        failed()
        listener.onPlayerError(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        // seekToNext() on the last item changes nothing: no onMediaItemTransition is raised.
        assertEquals(1, messages.size)
        verify(player, never()).prepare()
    }

    @Test
    fun `recovery applies only to the failed to next hop`() {
        failed()
        listener.onMediaItemTransition(item("B"), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) // A -> B
        healthy()
        listener.onPlaybackStateChanged(Player.STATE_READY) // B plays
        listener.onMediaItemTransition(item("C"), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) // B -> C
        verify(player, times(1)).prepare()
    }

    @Test
    fun `a failure on the next track is reported again`() {
        listener.onPlayerError(error(PlaybackException.ERROR_CODE_DECODING_FAILED)) // A
        failed()
        listener.onMediaItemTransition(item("B"), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        whenever(player.currentMediaItem).thenReturn(item("B"))
        listener.onPlayerError(error(PlaybackException.ERROR_CODE_DECODING_FAILED)) // B
        assertEquals(2, messages.size)
    }

    // --- Pure decision ----------------------------------------------------------------------------

    @Test
    fun `prepare is wanted only for a seek out of idle-with-error`() {
        val seek = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        assertTrue(shouldPrepareAfterTransition(seek, true, Player.STATE_IDLE))
        assertFalse(shouldPrepareAfterTransition(seek, false, Player.STATE_IDLE))
        assertFalse(shouldPrepareAfterTransition(seek, true, Player.STATE_READY))
        val auto = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        assertFalse(shouldPrepareAfterTransition(auto, true, Player.STATE_IDLE))
    }
}
