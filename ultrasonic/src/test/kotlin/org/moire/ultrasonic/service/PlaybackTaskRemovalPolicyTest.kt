/*
 * PlaybackTaskRemovalPolicyTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Issue #36 follow-up: removing Taki from Recents must not stop music the user still wants.
 * [shouldContinuePlaybackAfterTaskRemoved] is the whole decision; [MediaPlayerManager.isNetworkRecoveryPending]
 * is its only non-player input and is driven here through the real player listener.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackTaskRemovalPolicyTest {

    private fun keeps(
        playWhenReady: Boolean,
        state: Int,
        items: Int = 3,
        recovery: Boolean = false
    ) = shouldContinuePlaybackAfterTaskRemoved(playWhenReady, state, items, recovery)

    // --- the product policy table -------------------------------------------------------------

    @Test
    fun `playing keeps playing`() {
        assertTrue(keeps(playWhenReady = true, state = Player.STATE_READY))
    }

    @Test
    fun `buffering with play intent keeps recovering`() {
        // isPlaying() is false here; the Media3 default would have stopped the service.
        assertTrue(keeps(playWhenReady = true, state = Player.STATE_BUFFERING))
    }

    @Test
    fun `a transient audio focus suppression keeps the session`() {
        // READY + playWhenReady while suppressed: the other app's audio ends and Media3 resumes.
        assertTrue(keeps(playWhenReady = true, state = Player.STATE_READY))
    }

    @Test
    fun `paused finishes cleanly in every state`() {
        for (state in listOf(Player.STATE_READY, Player.STATE_BUFFERING, Player.STATE_IDLE, Player.STATE_ENDED)) {
            assertFalse("paused in state $state", keeps(playWhenReady = false, state = state))
        }
    }

    @Test
    fun `idle or stopped without a pending recovery finishes`() {
        assertFalse(keeps(playWhenReady = true, state = Player.STATE_IDLE))
    }

    @Test
    fun `idle while a network recovery is pending keeps trying`() {
        assertTrue(keeps(playWhenReady = true, state = Player.STATE_IDLE, recovery = true))
    }

    @Test
    fun `a pending recovery never keeps a paused player`() {
        assertFalse(keeps(playWhenReady = false, state = Player.STATE_IDLE, recovery = true))
    }

    @Test
    fun `an ended queue finishes`() {
        assertFalse(keeps(playWhenReady = true, state = Player.STATE_ENDED))
    }

    @Test
    fun `an empty queue finishes even with play intent`() {
        for (state in listOf(Player.STATE_READY, Player.STATE_BUFFERING, Player.STATE_IDLE)) {
            assertFalse(keeps(playWhenReady = true, state = state, items = 0, recovery = true))
        }
    }

    // --- the recovery flag is the real #19 state ----------------------------------------------

    private lateinit var manager: MediaPlayerManager
    private lateinit var listener: Player.Listener
    private lateinit var player: Player

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        manager = MediaPlayerManager(mock(), mock())
        player = mock()
        manager.setPrivateField("controller", player)
        manager.playbackErrorSink = { }
        listener = manager.getPrivateField("listeners") as Player.Listener
        whenever(player.playWhenReady).thenReturn(true)
    }

    private fun networkError() =
        PlaybackException("test", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

    @Test
    fun `no recovery is pending in a healthy session`() {
        assertFalse(manager.isNetworkRecoveryPending)
    }

    @Test
    fun `a transient network error schedules a recovery that the policy treats as continuing`() {
        listener.onPlayerError(networkError())
        assertTrue(manager.isNetworkRecoveryPending)
        assertTrue(keeps(true, Player.STATE_IDLE, recovery = manager.isNetworkRecoveryPending))
    }

    @Test
    fun `playback coming back clears the pending recovery`() {
        listener.onPlayerError(networkError())
        listener.onIsPlayingChanged(true)
        assertFalse(manager.isNetworkRecoveryPending)
        assertFalse(keeps(true, Player.STATE_IDLE, recovery = manager.isNetworkRecoveryPending))
    }

    @Test
    fun `a spent retry budget is no longer pending so a dead network cannot pin the service`() {
        manager.setPrivateField("networkErrorRetryCount", 3)
        listener.onPlayerError(networkError())
        assertFalse(manager.isNetworkRecoveryPending)
        assertEquals(false, keeps(true, Player.STATE_IDLE, recovery = manager.isNetworkRecoveryPending))
    }

    @Test
    fun `a pause during the backoff does not start a recovery`() {
        whenever(player.playWhenReady).thenReturn(false)
        listener.onPlayerError(networkError())
        assertFalse(manager.isNetworkRecoveryPending)
    }
}
