/*
 * AndroidAutoPlaylistCacheTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import java.util.concurrent.ExecutionException
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
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
 * Issue #43, AA-06: the Android Auto playlist cache was a single unkeyed `List<Track>` - whichever
 * playlist was fetched last - that [MediaLibrarySessionCallback.playPlaylist]/`playPlaylistSong` reused
 * for ANY playlist (and any server) while it was non-null. These tests drive the real callback through
 * its public Media3 entry points against a fake [MusicService] whose answers (and timing) the test controls.
 *
 *   server 1: playlist A (A1 A2 A3), playlist B (B1 B2 B3)        server 2: playlist A (S1 S2)
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoPlaylistCacheTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val session: MediaSession = mock()
    private val controller: MediaSession.ControllerInfo = mock()
    private val service: MusicService = mock()
    private lateinit var callback: MediaLibrarySessionCallback
    private var serverId = 1

    private val playlists = mutableMapOf<Pair<Int, String>, List<String>>()

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
        callback.ioDispatcher = Dispatchers.Unconfined // resolve inline: no real threads, no sleeps

        playlists[1 to "A"] = listOf("A1", "A2", "A3")
        playlists[1 to "B"] = listOf("B1", "B2", "B3")
        playlists[2 to "A"] = listOf("S1", "S2")
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer { inv ->
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

    private fun directory(ids: List<String>?): MusicDirectory {
        if (ids == null) throw IllegalStateException("playlist not found")
        return MusicDirectory().apply { ids.forEach { add(track(it)) } }
    }

    private fun track(id: String): Track {
        MediaItemConverter.addToCache(id, buildMediaItem(title = "Title $id", mediaId = id, isPlayable = true))
        return Track(id = id, title = "Title $id")
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun select(mediaId: String): com.google.common.util.concurrent.ListenableFuture<List<MediaItem>> {
        val future = callback.onAddMediaItems(
            session, controller, mutableListOf(buildMediaItem(title = mediaId, mediaId = mediaId, isPlayable = false))
        )
        idle()
        return future
    }

    private fun playAll(id: String) = select("MEDIA_PLAYLIST_ITEM|$id|name-$id")
    private fun playTrack(playlist: String, trackId: String) =
        select("MEDIA_PLAYLIST_SONG_ITEM|$playlist|name-$playlist|$trackId")

    private fun ids(f: com.google.common.util.concurrent.ListenableFuture<List<MediaItem>>) =
        f.get().map { it.mediaId }

    private fun com.google.common.util.concurrent.ListenableFuture<*>.failure(): Throwable =
        try {
            get()
            throw AssertionError("expected a failed future")
        } catch (e: ExecutionException) {
            e.cause!!
        }

    // ---- AA06-01..03: playlist identity ----

    @Test
    fun `play all returns the requested playlist after another one was resolved`() {
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
        assertEquals(listOf("B1", "B2", "B3"), ids(playAll("B")))
    }

    @Test
    fun `B then A then B keeps returning the right playlist`() {
        assertEquals(listOf("B1", "B2", "B3"), ids(playAll("B")))
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
        assertEquals(listOf("B1", "B2", "B3"), ids(playAll("B")))
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
    }

    @Test
    fun `a track is looked up in its own playlist`() {
        playAll("A")
        assertEquals(listOf("B2"), ids(playTrack("B", "B2")))
        // and a track of A is not found "by accident" in B's data
        assertEquals(listOf("A3"), ids(playTrack("A", "A3")))
    }

    @Test
    fun `a track that is not in the selected playlist is never taken from another one`() {
        playAll("A")
        assertTrue(playTrack("B", "A2").failure() is IllegalStateException)
    }

    @Test
    fun `equal titles in two playlists stay distinct`() {
        playlists[1 to "X"] = listOf("X1")
        playlists[1 to "Y"] = listOf("Y1")
        val titleOf = { f: com.google.common.util.concurrent.ListenableFuture<List<MediaItem>> ->
            f.get().map { it.mediaMetadata.title.toString() }
        }
        assertEquals(listOf("Title X1"), titleOf(playAll("X")))
        assertEquals(listOf("Title Y1"), titleOf(playAll("Y")))
    }

    // ---- AA06-06: server identity ----

    @Test
    fun `a playlist id used on another server never returns the first server's tracks`() {
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
        serverId = 2
        assertEquals(listOf("S1", "S2"), ids(playAll("A")))
        serverId = 1
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
    }

    // ---- AA06-07..10: concurrent requests ----

    @Test
    fun `two playlists resolving concurrently each get their own tracks, A finishing first`() {
        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer { inv ->
            val id = inv.getArgument<String>(0)
            (if (id == "A") gateA else gateB).await()
            directory(playlists[1 to id])
        }
        // two controllers (the car and the phone) ask for different playlists
        val a = playAll("A")
        val b = playAll("B")
        // the newer playback request supersedes the older one (AA-08); the newest keeps its own data
        gateA.complete(Unit)
        gateB.complete(Unit)
        idle()
        assertTrue(a.isCancelled)
        assertEquals(listOf("B1", "B2", "B3"), ids(b))
    }

    @Test
    fun `two playlists browsed concurrently keep their own lists when B finishes first`() {
        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer { inv ->
            val id = inv.getArgument<String>(0)
            (if (id == "A") gateA else gateB).await()
            directory(playlists[1 to id])
        }
        val library = mock<androidx.media3.session.MediaLibraryService.MediaLibrarySession>()
        val a = callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|A|name-A", 0, Int.MAX_VALUE, null)
        val b = callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|B|name-B", 0, Int.MAX_VALUE, null)
        idle()

        gateB.complete(Unit)
        idle()
        gateA.complete(Unit)
        idle()

        assertTrue(a.isDone && b.isDone)
        // Each was shown its own songs, and playing each plays that list.
        assertEquals("MEDIA_PLAYLIST_SONG_ITEM|A|name-A|A1|0", a.get().value!![1].mediaId)
        assertEquals("MEDIA_PLAYLIST_SONG_ITEM|B|name-B|B1|0", b.get().value!![1].mediaId)
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer { throw IllegalStateException("offline") }
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
        assertEquals(listOf("B1", "B2", "B3"), ids(playAll("B")))
    }

    @Test
    fun `two browses of the same playlist both complete`() {
        val gate = CompletableDeferred<Unit>()
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer {
            gate.await()
            directory(playlists[1 to "A"])
        }
        val library = mock<androidx.media3.session.MediaLibraryService.MediaLibrarySession>()
        val first = callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|A|name-A", 0, Int.MAX_VALUE, null)
        val second = callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|A|name-A", 0, Int.MAX_VALUE, null)
        gate.complete(Unit)
        idle()
        assertEquals("MEDIA_PLAYLIST_SONG_ITEM|A|name-A|A1|0", first.get().value!![1].mediaId)
        assertEquals("MEDIA_PLAYLIST_SONG_ITEM|A|name-A|A1|0", second.get().value!![1].mediaId)
    }

    @Test
    fun `describing a playlist node needs no request`() {
        val library = mock<androidx.media3.session.MediaLibraryService.MediaLibrarySession>()
        val item = callback.onGetItem(library, controller, "MEDIA_PLAYLIST_ITEM|A|My playlist")
        idle()
        assertEquals("MEDIA_PLAYLIST_ITEM|A|My playlist", item.get().value!!.mediaId)
        assertEquals("My playlist", item.get().value!!.mediaMetadata.title.toString())
        verifyBlocking(service, times(0)) { getPlaylist(any(), any()) }
    }

    @Test
    fun `a cancelled request that finishes late does not change the next result`() {
        val gateA = CompletableDeferred<Unit>()
        wheneverBlocking { service.getPlaylist(any(), any()) }.doSuspendableAnswer { inv ->
            val id = inv.getArgument<String>(0)
            if (id == "A") gateA.await()
            directory(playlists[1 to id])
        }
        val a = playAll("A")
        a.cancel(true)
        val b = playAll("B")
        gateA.complete(Unit) // the cancelled request's data arrives late
        idle()
        assertEquals(listOf("B1", "B2", "B3"), ids(b))
        assertEquals(listOf("B1", "B2", "B3"), ids(playAll("B")))
        gateA.complete(Unit)
    }

    // ---- AA06-11..13: failures must not poison, empty / deleted playlists ----

    @Test
    fun `a failed lookup does not leave a wrong or empty cache behind`() {
        playlists.remove(1 to "A")
        assertTrue(playAll("A").failure() is IllegalStateException)
        playlists[1 to "A"] = listOf("A1", "A2", "A3") // the server recovers
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
    }

    @Test
    fun `an empty playlist is not replaced by another playlist's tracks`() {
        playlists[1 to "E"] = emptyList()
        assertEquals(listOf("A1", "A2", "A3"), ids(playAll("A")))
        assertTrue(playAll("E").failure() is IllegalStateException)
    }

    @Test
    fun `a deleted playlist fails instead of playing the previous one`() {
        assertEquals(listOf("B1", "B2", "B3"), ids(playAll("B")))
        playlists.remove(1 to "B")
        assertTrue(playAll("B").failure() is IllegalStateException)
    }

    // ---- AA06-14 / 15: order and freshness ----

    @Test
    fun `the playlist order is preserved`() {
        playlists[1 to "O"] = listOf("O3", "O1", "O2")
        assertEquals(listOf("O3", "O1", "O2"), ids(playAll("O")))
    }

    @Test
    fun `a browsed playlist is played from the same snapshot, a new browse refreshes it`() {
        val library = mock<androidx.media3.session.MediaLibraryService.MediaLibrarySession>()
        callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|A|name-A", 0, Int.MAX_VALUE, null)
        idle()
        verifyBlocking(service, times(1)) { getPlaylist("A", "name-A") }

        // The playlist changes on the server, but the user is still inside the list they browsed:
        playlists[1 to "A"] = listOf("A9")
        assertEquals("playing what was shown", listOf("A1", "A2", "A3"), ids(playAll("A")))
        verifyBlocking(service, times(1)) { getPlaylist("A", "name-A") } // no second request

        // Re-opening the playlist refreshes the snapshot.
        callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|A|name-A", 0, Int.MAX_VALUE, null)
        idle()
        assertEquals(listOf("A9"), ids(playAll("A")))
    }

    @Test
    fun `a playlist that was not browsed is fetched fresh for each selection`() {
        playAll("B")
        playlists[1 to "B"] = listOf("B7")
        assertEquals(listOf("B7"), ids(playAll("B")))
        assertFalse(playTrack("B", "B7").isCancelled)
        verifyBlocking(service, times(3)) { getPlaylist("B", "name-B") }
    }

    // ---- the same identity rule for the random list (same class of defect) ----

    @Test
    fun `the shown random list is played on its own server only`() {
        val randomFor = mapOf(1 to listOf("R1", "R2"), 2 to listOf("Q1"))
        wheneverBlocking { service.getRandomSongs(any()) }.doSuspendableAnswer { directory(randomFor[serverId]) }
        val library = mock<androidx.media3.session.MediaLibraryService.MediaLibrarySession>()
        callback.onGetChildren(library, controller, "MEDIA_SONG_RANDOM_ID", 0, Int.MAX_VALUE, null)
        idle()

        assertEquals(listOf("R1", "R2"), ids(select("MEDIA_SONG_RANDOM_ID")))
        serverId = 2
        assertEquals("another server never gets the first server's songs", listOf("Q1"), ids(select("MEDIA_SONG_RANDOM_ID")))
    }
}
