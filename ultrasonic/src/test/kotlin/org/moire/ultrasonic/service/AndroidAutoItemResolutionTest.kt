/*
 * AndroidAutoItemResolutionTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Issue #43, AA-08: Android Auto's media-id-only selections (legacy path of `onAddMediaItems`) and
 * `onGetItem` used to resolve their tracks with `serviceScope.future { ... }.get()` - a blocking wait
 * for the server on the session's application (main) looper. They now resolve asynchronously: the
 * callback returns a pending future immediately and completes it later, with a controlled failure on
 * timeout, a malformed id, an empty result, a server switch or cancellation.
 *
 * The resolver is replaced by a fake that stays unresolved until the test releases it, so nothing here
 * depends on wall-clock time or a network.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoItemResolutionTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val session: MediaSession = mock()
    // Taki's own controller: only it may supply a playback source uri (#46); media ids work for any.
    private val controller: MediaSession.ControllerInfo = mock<MediaSession.ControllerInfo>().also {
        whenever(it.uid).thenReturn(android.os.Process.myUid())
    }
    private lateinit var callback: MediaLibrarySessionCallback
    private var serverId = 1

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
        // The playback infrastructure is "already running": the request is released immediately.
        doAnswer { it.getArgument<Runnable?>(0)?.run() }
            .whenever(lifecycleSupport).ensureCreatedForPlayback(anyOrNull())
        callback = MediaLibrarySessionCallback()
        callback.activeServerId = { serverId }
    }

    @After
    fun tearDown() {
        callback.release()
        stopKoin()
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun track(id: String): Track {
        val track = Track(id = id, title = "Title $id")
        // Pre-fill the converter cache so toMediaItem() needs no storage or settings.
        MediaItemConverter.addToCache(
            id,
            buildMediaItem(title = "Title $id", mediaId = id, isPlayable = true)
        )
        return track
    }

    private fun legacySelection(id: String = "MEDIA_ALBUM_ITEM|album|name") =
        mutableListOf(buildMediaItem(title = id, mediaId = id, isPlayable = false))

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> com.google.common.util.concurrent.ListenableFuture<T>.failure(): Throwable =
        try {
            get()
            throw AssertionError("expected a failed future")
        } catch (e: ExecutionException) {
            e.cause!!
        }

    // ---- AA08-01 / 02 / 07: returns at once, completes later, concurrent requests ----

    @Test
    fun `a legacy selection returns a pending future without waiting for the server`() {
        val gate = CompletableDeferred<List<Track>>()
        callback.mediaIdResolver = { gate.await() }

        val result = callback.onAddMediaItems(session, controller, legacySelection())

        // The callback has returned (this line runs) while the "server" has not answered.
        idle()
        assertFalse(result.isDone)

        gate.complete(listOf(track("t1"), track("t2")))
        idle()

        assertTrue(result.isDone)
        assertEquals(listOf("t1", "t2"), result.get().map { it.mediaId })
    }

    @Test
    fun `concurrent resolutions complete independently`() {
        val gate = CompletableDeferred<List<Track>>()
        callback.mediaIdResolver = { id -> if (id.endsWith("slow")) gate.await() else listOf(track("fast")) }

        val slow = callback.onGetItem(session.libraryOf(), controller, "MEDIA_ALBUM_SONG_ITEM|a|n|slow")
        val fast = callback.onGetItem(session.libraryOf(), controller, "MEDIA_ALBUM_SONG_ITEM|a|n|fast")
        idle()

        assertFalse(slow.isDone)
        assertTrue("a slow request must not hold up another", fast.isDone)
        assertEquals("fast", fast.get().value!!.mediaId)

        gate.complete(listOf(track("slow")))
        idle()
        assertEquals("slow", slow.get().value!!.mediaId)
    }

    // ---- AA08-03 / 04 / 09: failure, timeout, invalid id ----

    @Test
    fun `a resolution that finds nothing fails the request`() {
        callback.mediaIdResolver = { null }

        val result = callback.onAddMediaItems(session, controller, legacySelection())
        idle()

        assertTrue(result.failure() is IllegalStateException)
    }

    @Test
    fun `a request that never completes fails after the timeout instead of hanging`() {
        callback.resolutionTimeoutMs = 30_000
        callback.mediaIdResolver = { awaitCancellation() }

        val result = callback.onAddMediaItems(session, controller, legacySelection())
        idle()
        assertFalse(result.isDone)

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(30_100))

        assertTrue(result.isDone)
        assertTrue(result.failure() is IllegalStateException)
    }

    @Test
    fun `a malformed media id is rejected without crashing`() {
        callback.mediaIdResolver = { throw IndexOutOfBoundsException("bad id") }

        val result = callback.onAddMediaItems(session, controller, legacySelection("MEDIA_ALBUM_ITEM"))
        idle()

        assertTrue(result.failure() is IllegalArgumentException)
    }

    @Test
    fun `onGetItem answers with an error result for an unresolvable id`() {
        callback.mediaIdResolver = { emptyList() }

        val result = callback.onGetItem(session.libraryOf(), controller, "MEDIA_ROOT_ID")
        idle()

        assertEquals(SessionError.ERROR_BAD_VALUE, result.get().resultCode)
    }

    // ---- AA08-05 / 06: cancellation, rapid selection ----

    @Test
    fun `a newer playback request supersedes the one still resolving`() {
        val gateA = CompletableDeferred<List<Track>>()
        val gateB = CompletableDeferred<List<Track>>()
        callback.mediaIdResolver = { id -> if (id.contains("trackA")) gateA.await() else gateB.await() }

        val a = callback.onAddMediaItems(session, controller, legacySelection("MEDIA_ALBUM_SONG_ITEM|x|n|trackA"))
        val b = callback.onAddMediaItems(session, controller, legacySelection("MEDIA_ALBUM_SONG_ITEM|x|n|trackB"))
        idle()

        assertTrue("the superseded request is cancelled", a.isCancelled)

        // The old response arrives late: it must not produce a result for the queue.
        gateA.complete(listOf(track("trackA")))
        gateB.complete(listOf(track("trackB")))
        idle()

        assertEquals(listOf("trackB"), b.get().map { it.mediaId })
        assertTrue(a.isCancelled)
    }

    @Test
    fun `cancelling a pending request stops its resolution`() {
        var cancelled = false
        callback.mediaIdResolver = {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }

        val result = callback.onAddMediaItems(session, controller, legacySelection())
        idle()
        result.cancel(true)
        idle()

        assertTrue("the work behind a cancelled request is cancelled too", cancelled)
    }

    // ---- server change, service destruction ----

    @Test
    fun `tracks resolved for the previous server are rejected after a server switch`() {
        val gate = CompletableDeferred<List<Track>>()
        callback.mediaIdResolver = { gate.await() }

        val result = callback.onAddMediaItems(session, controller, legacySelection())
        idle()
        serverId = 2 // the user switched servers while the request was in flight
        gate.complete(listOf(track("old-server-track")))
        idle()

        assertTrue(result.failure() is IllegalStateException)
    }

    @Test
    fun `releasing the callback cancels requests still in flight`() {
        var cancelled = false
        callback.mediaIdResolver = {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val item = callback.onGetItem(session.libraryOf(), controller, "MEDIA_ALBUM_ITEM|a|n")
        val playback = callback.onAddMediaItems(session, controller, legacySelection())
        idle()

        callback.release() // service destroyed
        idle()

        assertTrue(cancelled)
        assertTrue(item.isCancelled || item.isDone)
        assertTrue(playback.isCancelled || playback.isDone)
    }

    // ---- playback queue integrity, AA-01 ----

    @Test
    fun `a resolved album keeps its order`() {
        callback.mediaIdResolver = { listOf(track("1"), track("2"), track("3")) }

        val result = callback.onAddMediaItems(session, controller, legacySelection())
        idle()

        assertEquals(listOf("1", "2", "3"), result.get().map { it.mediaId })
    }

    @Test
    fun `an item that already carries its uri is not resolved at all`() {
        var called = false
        callback.mediaIdResolver = { called = true; null }
        val direct: MediaItem = buildMediaItem(
            title = "direct", mediaId = "d", isPlayable = true,
            sourceUri = android.net.Uri.parse("d|0|/music/d.flac")
        )

        val result = callback.onAddMediaItems(session, controller, mutableListOf(direct))
        idle()

        assertEquals(listOf("d"), result.get().map { it.mediaId })
        assertFalse(called)
    }

    @Test
    fun `an empty selection never touches the resolver or the infrastructure`() {
        var called = false
        callback.mediaIdResolver = { called = true; null }

        callback.onAddMediaItems(session, controller, mutableListOf())

        assertFalse(called)
    }

    private fun MediaSession.libraryOf() =
        mock<androidx.media3.session.MediaLibraryService.MediaLibrarySession>()
}
