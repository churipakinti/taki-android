/*
 * AndroidAutoRepeatModeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Bundle
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.data.ActiveServerProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Issue #43, AA-03 / AA-04. Android Auto is just another controller of the single player: connecting
 * and disconnecting it must not change the user's repeat mode, must not leave anything registered
 * with the system (the old `CarConnection.type.observeForever` registered a broadcast receiver per
 * `onConnect` and was never removed), and a repeat change from any controller must stay the
 * authoritative state that every controller's buttons show.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoRepeatModeTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val controller: MediaSession.ControllerInfo = mock()
    private val player: Player = mock()
    private val session: MediaSession = mock()
    private var repeat = Player.REPEAT_MODE_OFF

    private lateinit var callback: MediaLibrarySessionCallback

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        startKoin {
            modules(
                module {
                    single { lifecycleSupport }
                    single { mock<MediaPlayerManager>() }
                    single { mock<ActiveServerProvider>() }
                }
            )
        }
        // A tiny stateful player: the real Player stores whatever repeat mode it is given.
        whenever(player.repeatMode).thenAnswer { repeat }
        doAnswer { repeat = it.getArgument(0); null }.whenever(player).repeatMode = any()
        whenever(session.player).thenReturn(player)
        callback = MediaLibrarySessionCallback()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun connect() = callback.onConnect(session, controller)

    private fun repeatCommand() = SessionCommand(PlaybackService.CUSTOM_COMMAND_REPEAT_MODE, Bundle.EMPTY)

    private fun userTapsRepeatInCar() {
        callback.onCustomCommand(session, controller, repeatCommand(), Bundle.EMPTY)
    }

    private fun lastLayoutRepeatName(): String {
        val layout = argumentCaptor<ImmutableList<CommandButton>>()
        verify(session, atLeastOnce()).setCustomLayout(layout.capture())
        return layout.lastValue.map { it.displayName.toString() }.first { it.startsWith("Repeat") }
    }

    // ---- AA-04: the user's repeat mode is never changed by connecting ----

    @Test
    fun `connecting never changes any repeat mode`() {
        for (mode in intArrayOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL)) {
            repeat = mode
            connect()
            connect()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("mode $mode must survive connects", mode, repeat)
        }
        verify(player, never()).repeatMode = any()
    }

    @Test
    fun `a repeat change made in the car is never reverted by later connections`() {
        repeat = Player.REPEAT_MODE_OFF
        connect()

        userTapsRepeatInCar()
        assertEquals(Player.REPEAT_MODE_ALL, repeat)

        // "disconnect" and reconnect several times: nothing may restore an older snapshot
        repeat(3) { connect() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Player.REPEAT_MODE_ALL, repeat)

        userTapsRepeatInCar()
        userTapsRepeatInCar()
        assertEquals("ALL -> ONE -> OFF", Player.REPEAT_MODE_OFF, repeat)
        connect()
        assertEquals(Player.REPEAT_MODE_OFF, repeat)
    }

    @Test
    fun `the repeat command cycles Off All One Off`() {
        repeat = Player.REPEAT_MODE_OFF
        val seen = mutableListOf<Int>()
        repeat(4) {
            userTapsRepeatInCar()
            seen += repeat
        }
        assertEquals(
            listOf(Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL),
            seen
        )
    }

    // ---- synchronisation of the buttons with the authoritative player state ----

    @Test
    fun `the repeat button follows the player state after a car tap`() {
        repeat = Player.REPEAT_MODE_OFF
        userTapsRepeatInCar()
        assertEquals("Repeat All", lastLayoutRepeatName())
        userTapsRepeatInCar()
        assertEquals("Repeat One", lastLayoutRepeatName())
    }

    @Test
    fun `a repeat change from another controller can refresh the buttons`() {
        repeat = Player.REPEAT_MODE_ONE // changed on the phone, not through a custom command

        callback.refreshCustomLayout(session)

        assertEquals("Repeat One", lastLayoutRepeatName())
    }

    // ---- AA-03: nothing is registered with the system by connecting ----

    @Test
    fun `repeated connections register no receiver`() {
        val application = RuntimeEnvironment.getApplication()
        val before = shadowOf(application).registeredReceivers.size

        repeat(10) { connect() }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("onConnect must not leak a broadcast receiver per controller", before,
            shadowOf(application).registeredReceivers.size)
    }

    @Test
    fun `connecting does not start the playback infrastructure`() {
        repeat(3) { connect() }
        verify(lifecycleSupport, never()).ensureCreatedForPlayback(org.mockito.kotlin.anyOrNull())
    }
}
