/*
 * AndroidAutoPlaybackStartTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner

/**
 * Issue #43 / AA-01, session-callback side: which Android Auto requests start the playback
 * infrastructure. Only a playback request does; connecting and browsing never do (no playback, no
 * queue restoration, no Navidrome traffic merely because the car connected).
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoPlaybackStartTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val session: MediaSession = mock()
    private val controller: MediaSession.ControllerInfo = mock()

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
        callback = MediaLibrarySessionCallback()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun playableItem(id: String): MediaItem = buildMediaItem(
        title = id,
        mediaId = id,
        isPlayable = true,
        sourceUri = Uri.parse("$id|0|/music/$id.flac")
    )

    @Test
    fun `a playback request starts the infrastructure and is held until it is ready`() {
        val request = mutableListOf(playableItem("t1"))

        val result = callback.onAddMediaItems(session, controller, request)

        val onReady = argumentCaptor<Runnable>()
        verify(lifecycleSupport).ensureCreatedForPlayback(onReady.capture())
        assertFalse("the items are not handed to the player before the controller is up", result.isDone)

        onReady.firstValue.run()

        assertTrue(result.isDone)
        assertEquals(listOf("t1"), result.get().map { it.mediaId })
    }

    @Test
    fun `a playback request is not held when the infrastructure is already running`() {
        // Already running: ensureCreatedForPlayback runs its callback immediately.
        doAnswer { (it.getArgument<Runnable?>(0))?.run() }
            .whenever(lifecycleSupport).ensureCreatedForPlayback(anyOrNull())

        val result = callback.onAddMediaItems(session, controller, mutableListOf(playableItem("t1")))

        assertTrue(result.isDone)
        verify(lifecycleSupport, times(1)).ensureCreatedForPlayback(anyOrNull())
    }

    @Test
    fun `an empty request does not start anything`() {
        callback.onAddMediaItems(session, controller, mutableListOf())

        verify(lifecycleSupport, never()).ensureCreatedForPlayback(anyOrNull())
        verify(lifecycleSupport, never()).onCreate()
    }

    @Test
    fun `connecting and browsing do not start the playback infrastructure`() {
        repeat(3) { callback.onGetLibraryRoot(mock(), controller, null) }
        callback.onGetItem(mock(), controller, "MEDIA_ROOT_ID")

        verify(lifecycleSupport, never()).ensureCreatedForPlayback(anyOrNull())
        verify(lifecycleSupport, never()).onCreate()
        verify(lifecycleSupport, never()).onCreate(any(), anyOrNull())
    }
}
