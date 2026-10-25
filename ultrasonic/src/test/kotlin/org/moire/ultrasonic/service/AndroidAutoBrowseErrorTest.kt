/*
 * AndroidAutoBrowseErrorTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Issue #43, AA-10: a failed browse is an error, not an empty list; browse futures are bounded, reject a
 * response that arrives after the server changed, and unknown ids are bad values. A server change tells
 * the host (notifyChildrenChanged) to reload the catalog, without touching playback.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoBrowseErrorTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val controller: MediaSession.ControllerInfo = mock()
    private val library: MediaLibraryService.MediaLibrarySession = mock()
    private val service: MusicService = mock()
    private lateinit var callback: MediaLibrarySessionCallback
    private var serverId = 1

    private val playlists = mutableMapOf<Pair<Int, String>, List<String>>()
    private var failing = false
    private var requests = 0

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
        doAnswer { it.getArgument<Runnable?>(0)?.run() }
            .whenever(lifecycleSupport).ensureCreatedForPlayback(anyOrNull())
        callback = MediaLibrarySessionCallback()
        callback.activeServerId = { serverId }
        callback.musicServiceProvider = { service }
        callback.ioDispatcher = Dispatchers.Unconfined

        wheneverBlocking { service.getPlaylist(any(), any()) }.thenAnswer { inv ->
            requests++
            if (failing) throw java.io.IOException("server unreachable")
            directory(playlists[serverId to inv.getArgument<String>(0)])
        }
    }

    @After
    fun tearDown() {
        callback.release()
        stopKoin()
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun track(id: String): Track {
        MediaItemConverter.addToCache(id, buildMediaItem(title = "Title $id", mediaId = id, isPlayable = true))
        return Track(id = id, title = "Title $id")
    }

    private fun directory(ids: List<String>?): MusicDirectory {
        if (ids == null) throw IllegalStateException("playlist not found")
        return MusicDirectory().apply { ids.forEach { add(track(it)) } }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun browse(parentId: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = callback.onGetChildren(library, controller, parentId, 0, Int.MAX_VALUE, null)
        idle()
        return future
    }

    private fun LibraryResult<ImmutableList<MediaItem>>.rows() = value!!.map { it.mediaId }

    private fun ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>.result() =
        get(WAIT_S, TimeUnit.SECONDS)

    private val playlistA = "MEDIA_PLAYLIST_ITEM|A|name-A"

    // ---- empty versus failed ----

    @Test
    fun `a playlist with tracks lists them`() {
        playlists[1 to "A"] = listOf("A1", "A2")
        assertEquals(3, browse(playlistA).result().value!!.size) // Play All + 2
    }

    @Test
    fun `a valid empty playlist is a successful empty result`() {
        playlists[1 to "A"] = emptyList()
        val result = browse(playlistA).result()
        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(0, result.value!!.size)
    }

    @Test
    fun `a server error is an error result, not an empty list`() {
        playlists[1 to "A"] = listOf("A1")
        failing = true
        val result = browse(playlistA).result()
        assertEquals(SessionError.ERROR_IO, result.resultCode)
    }

    @Test
    fun `a request that never answers fails after the timeout`() {
        callback.resolutionTimeoutMs = 30_000
        wheneverBlocking { service.getPlaylist(any(), any()) }
            .doSuspendableAnswer { CompletableDeferred<MusicDirectory>().await() }
        val future = browse(playlistA)
        assertFalse(future.isDone)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(30_100))
        assertEquals(SessionError.ERROR_IO, future.result().resultCode)
    }

    @Test
    fun `an unknown or malformed parent id is a bad value, not an empty folder`() {
        assertEquals(SessionError.ERROR_BAD_VALUE, browse("NOT_A_NODE").result().resultCode)
        assertEquals(SessionError.ERROR_BAD_VALUE, browse("MEDIA_PLAYLIST_ITEM").result().resultCode)
    }

    // ---- recovery, server identity ----

    @Test
    fun `after a failure the same node loads once the server answers, nothing stays poisoned`() {
        playlists[1 to "A"] = listOf("A1", "A2")
        failing = true
        assertEquals(SessionError.ERROR_IO, browse(playlistA).result().resultCode)
        failing = false
        assertEquals(3, browse(playlistA).result().value!!.size)
    }

    @Test
    fun `a failed refresh does not replace the list a tap would play`() {
        playlists[1 to "A"] = listOf("A1", "A2", "A3")
        browse(playlistA).result()
        failing = true
        assertEquals(SessionError.ERROR_IO, browse(playlistA).result().resultCode)
        failing = false // a tap resolves from what was shown, with no new request
        val before = requests
        val play = callback.onSetMediaItems(
            mock(),
            controller,
            mutableListOf(buildMediaItem(title = "x", mediaId = "MEDIA_PLAYLIST_ITEM|A|name-A", isPlayable = false)),
            C.INDEX_UNSET,
            C.TIME_UNSET
        )
        idle()
        assertEquals(listOf("A1", "A2", "A3"), play.get(WAIT_S, TimeUnit.SECONDS).mediaItems.map { it.mediaId })
        assertEquals(before, requests)
    }

    @Test
    fun `another server shows its own content`() {
        playlists[1 to "A"] = listOf("A1")
        playlists[2 to "A"] = listOf("S1", "S2")
        assertEquals(listOf("MEDIA_PLAYLIST_SONG_ITEM|A|name-A|A1|0"), browse(playlistA).result().rows())
        serverId = 2
        assertEquals(3, browse(playlistA).result().value!!.size) // Play All + S1 + S2
    }

    @Test
    fun `a response that arrives after the server changed is rejected`() {
        val gate = CompletableDeferred<MusicDirectory>()
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer { gate.await() }
        val future = browse(playlistA)
        serverId = 2
        gate.complete(directory(listOf("A1", "A2")))
        idle()
        assertEquals(SessionError.ERROR_INVALID_STATE, future.result().resultCode)
    }

    @Test
    fun `two nodes browsed together each get their own content, whichever finishes first`() {
        playlists[1 to "A"] = listOf("A1")
        playlists[1 to "B"] = listOf("B1", "B2")
        val gate = CompletableDeferred<Unit>()
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer { inv ->
            val id = inv.getArgument<String>(0)
            if (id == "A") gate.await()
            directory(playlists[1 to id])
        }
        val a = browse("MEDIA_PLAYLIST_ITEM|A|name-A")
        val b = browse("MEDIA_PLAYLIST_ITEM|B|name-B")
        assertTrue("B does not wait for A", b.isDone)
        assertEquals(3, b.result().value!!.size) // Play All + B1 + B2
        gate.complete(Unit)
        idle()
        assertEquals(listOf("MEDIA_PLAYLIST_SONG_ITEM|A|name-A|A1|0"), a.result().rows()) // one song: no Play All
    }

    // ---- no side effects ----

    @Test
    fun `browsing, failing or not, never starts playback`() {
        playlists[1 to "A"] = listOf("A1", "A2")
        browse(playlistA)
        failing = true
        browse(playlistA)
        verify(lifecycleSupport, never()).ensureCreatedForPlayback(anyOrNull())
    }

    // ---- catalog refresh ----

    @Test
    fun `a server change asks the host to reload the catalog roots and nothing else`() {
        callback.notifyCatalogChanged(library)

        val parents = argumentCaptor<String>()
        verify(library, atLeastOnce()).notifyChildrenChanged(parents.capture(), any(), anyOrNull())
        assertTrue(parents.allValues.contains("MEDIA_ROOT_ID"))
        assertTrue(parents.allValues.contains("MEDIA_PLAYLIST_ID"))
        assertEquals("each node once", parents.allValues.size, parents.allValues.toSet().size)
        verify(lifecycleSupport, never()).ensureCreatedForPlayback(anyOrNull())
    }

    @Test
    fun `a plain browse never notifies, so there is no refresh loop`() {
        playlists[1 to "A"] = listOf("A1")
        browse(playlistA)
        browse(playlistA)
        verify(library, never()).notifyChildrenChanged(any<String>(), any(), anyOrNull())
    }
}

private const val WAIT_S = 2L
