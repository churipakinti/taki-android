/*
 * ControllerAuthorizationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.StarRating
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.di.ONLINE_MUSIC_SERVICE
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * AA-05. `PlaybackService` is exported, so any installed app can connect to it as a Media3 controller.
 * What it may do must follow what Android attests ([MediaSession.ControllerInfo.isTrusted]), and the
 * decision is enforced where the command is executed - not only by which commands onConnect offered.
 *
 * Untrusted controllers keep browsing and ordinary transport (including shuffle/repeat), but can never
 * star/unstar/rate: the MediaItem, the Track, [RatingManager] and the server must stay untouched.
 */
@RunWith(RobolectricTestRunner::class)
class ControllerAuthorizationTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val untrusted: MediaSession.ControllerInfo = mock()
    private val trusted: MediaSession.ControllerInfo = mock()
    private val player: Player = mock()
    private val session: MediaLibraryService.MediaLibrarySession = mock()
    private val musicService: MusicService = mock()

    private lateinit var callback: MediaLibrarySessionCallback
    private lateinit var current: MediaItem

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        startKoin {
            modules(
                module {
                    single { lifecycleSupport }
                    single { mock<MediaPlayerManager>() }
                    single { mock<ActiveServerProvider>() }
                    single<MusicService>(named(ONLINE_MUSIC_SERVICE)) { musicService }
                    single<MusicService>(named(org.moire.ultrasonic.di.OFFLINE_MUSIC_SERVICE)) { musicService }
                }
            )
        }
        whenever(untrusted.isTrusted).thenReturn(false)
        whenever(untrusted.uid).thenReturn(Process.myUid() + 1)
        whenever(trusted.isTrusted).thenReturn(true)
        whenever(trusted.uid).thenReturn(Process.myUid() + 2)

        current = MediaItem.Builder()
            .setMediaId("current-track")
            .setMediaMetadata(
                MediaMetadata.Builder().setExtras(Bundle().apply { putBoolean("starred", false) }).build()
            )
            .build()
        whenever(player.currentMediaItem).thenReturn(current)
        whenever(session.player).thenReturn(player)
        RatingManager.instance.lastUpdate = null
        callback = MediaLibrarySessionCallback()
    }

    @After
    fun tearDown() {
        RatingManager.instance.lastUpdate = null
        stopKoin()
    }

    private fun isStarred() = current.mediaMetadata.extras!!.getBoolean("starred")

    private fun assertDenied(future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult>) {
        val result = future.get()
        assertEquals(SessionError.ERROR_PERMISSION_DENIED, result.sessionError?.code)
    }

    private fun assertNothingMutated() {
        assertFalse("the MediaItem must not be starred", isStarred())
        assertNull("RatingManager must not have been invoked", RatingManager.instance.lastUpdate)
        verify(player, never()).repeatMode = any()
    }

    private fun custom(action: String) = SessionCommand(action, Bundle.EMPTY)

    // ---- what is advertised ----

    @Test
    fun `an untrusted controller is offered no rating or heart command`() {
        val commands = callback.onConnect(session, untrusted).availableSessionCommands

        assertFalse(commands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
        assertFalse(commands.contains(custom(PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON)))
        assertFalse(commands.contains(custom(PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF)))
    }

    @Test
    fun `an untrusted controller keeps browsing and the transport custom commands`() {
        val result = callback.onConnect(session, untrusted)

        assertTrue(result.isAccepted)
        assertTrue(result.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
        assertTrue(result.availableSessionCommands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertTrue(result.availableSessionCommands.contains(custom(PlaybackService.CUSTOM_COMMAND_SHUFFLE)))
        assertTrue(result.availableSessionCommands.contains(custom(PlaybackService.CUSTOM_COMMAND_REPEAT_MODE)))
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM))
    }

    @Test
    fun `a trusted controller keeps every rating and heart command`() {
        val commands = callback.onConnect(session, trusted).availableSessionCommands

        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
        assertTrue(commands.contains(custom(PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON)))
        assertTrue(commands.contains(custom(PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF)))
        assertTrue(commands.contains(custom(PlaybackService.CUSTOM_COMMAND_SHUFFLE)))
    }

    @Test
    fun `Taki's own process and its media notification controller are trusted even if not attested`() {
        val sameUid: MediaSession.ControllerInfo = mock()
        whenever(sameUid.isTrusted).thenReturn(false)
        whenever(sameUid.uid).thenReturn(Process.myUid())
        val notification: MediaSession.ControllerInfo = mock()
        whenever(notification.isTrusted).thenReturn(false)
        whenever(notification.uid).thenReturn(Process.myUid() + 3)
        whenever(session.isMediaNotificationController(notification)).thenReturn(true)

        for (controller in listOf(sameUid, notification)) {
            assertTrue(
                callback.onConnect(session, controller).availableSessionCommands
                    .contains(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
            )
        }
    }

    // ---- what is enforced at execution, whatever was advertised ----

    @Test
    fun `an untrusted controller cannot star or unstar through the current-item rating`() {
        assertDenied(callback.onSetRating(session, untrusted, HeartRating(true)))
        assertDenied(callback.onSetRating(session, untrusted, HeartRating(false)))
        assertNothingMutated()
    }

    @Test
    fun `an untrusted controller cannot rate an arbitrary media id`() {
        assertDenied(callback.onSetRating(session, untrusted, "any-track-id", HeartRating(true)))
        assertDenied(callback.onSetRating(session, untrusted, "any-track-id", HeartRating(false)))
        assertDenied(callback.onSetRating(session, untrusted, "any-track-id", StarRating(5, 5f)))
        assertDenied(callback.onSetRating(session, untrusted, StarRating(5, 5f)))
        shadowOf(Looper.getMainLooper()).idle()
        assertNothingMutated()
        verifyBlocking(musicService, never()) { star(any(), any(), any()) }
        verifyBlocking(musicService, never()) { unstar(any(), any(), any()) }
        verify(musicService, never()).setRating(any(), any())
    }

    @Test
    fun `an untrusted controller cannot run the heart custom commands even though they are unadvertised`() {
        for (action in listOf(
            PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON,
            PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_OFF
        )) {
            assertDenied(callback.onCustomCommand(session, untrusted, custom(action), Bundle.EMPTY))
        }
        assertNothingMutated()
        // not even the custom layout / button state may change
        verify(session, never()).setCustomLayout(any())
    }

    @Test
    fun `unknown and placeholder custom commands fail safely for every controller`() {
        for (controller in listOf(untrusted, trusted)) {
            for (action in listOf("totally.unknown", "", PlaybackService.CUSTOM_COMMAND_PLACEHOLDER)) {
                val result = callback.onCustomCommand(session, controller, custom(action), Bundle.EMPTY).get()
                assertTrue("$action must not succeed", result.resultCode != 0)
            }
        }
        assertNothingMutated()
    }

    @Test
    fun `an untrusted controller can still use shuffle and repeat`() {
        val before = player.repeatMode
        val repeat = callback.onCustomCommand(
            session, untrusted, custom(PlaybackService.CUSTOM_COMMAND_REPEAT_MODE), Bundle.EMPTY
        ).get()
        val shuffle = callback.onCustomCommand(
            session, untrusted, custom(PlaybackService.CUSTOM_COMMAND_SHUFFLE), Bundle.EMPTY
        ).get()

        assertEquals(0, repeat.resultCode)
        assertEquals(0, shuffle.resultCode)
        verify(player).repeatMode = any()
        assertEquals(before, player.repeatMode) // mock player: only the call matters
    }

    @Test
    fun `a trusted controller can still star the current item`() {
        val result = callback.onSetRating(session, trusted, HeartRating(true))

        assertTrue("the MediaItem is starred before the rating is submitted", isStarred())
        // not an immediate permission error (the submission itself is asynchronous)
        assertFalse(result.isDone && result.get().sessionError?.code == SessionError.ERROR_PERMISSION_DENIED)
    }

    @Test
    fun `a trusted controller can use the heart custom command`() {
        val result = callback.onCustomCommand(
            session, trusted, custom(PlaybackService.CUSTOM_COMMAND_TOGGLE_HEART_ON), Bundle.EMPTY
        )

        assertTrue(isStarred())
        assertFalse(result.isDone && result.get().sessionError?.code == SessionError.ERROR_PERMISSION_DENIED)
    }

    @Test
    fun `authorization is per controller - an untrusted one never inherits a trusted one's access`() {
        callback.onConnect(session, trusted)
        callback.onSetRating(session, trusted, HeartRating(true))
        callback.onSetRating(session, trusted, HeartRating(false))
        current.mediaMetadata.extras!!.putBoolean("starred", false)

        assertDenied(callback.onSetRating(session, untrusted, HeartRating(true)))
        assertFalse(isStarred())

        // and the other way round: the untrusted attempt does not lock the trusted one out
        callback.onSetRating(session, trusted, HeartRating(true))
        assertTrue(isStarred())
    }

    @Test
    fun `invalid input does not crash an untrusted controller's request`() {
        assertDenied(callback.onSetRating(session, untrusted, "", HeartRating(true)))
        assertDenied(callback.onSetRating(session, untrusted, "x".repeat(10_000), HeartRating(true)))
        assertDenied(callback.onSetRating(session, untrusted, "id|with|pipes|\u0000", StarRating(5, 0f)))
        assertNothingMutated()
    }

    @Test
    fun `with no current item an untrusted rating is still denied, not forwarded`() {
        whenever(player.currentMediaItem).thenReturn(null)

        assertDenied(callback.onSetRating(session, untrusted, HeartRating(true)))
        assertNothingMutated()
    }
}
