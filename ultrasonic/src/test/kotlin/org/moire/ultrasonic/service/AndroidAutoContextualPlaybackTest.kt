/*
 * AndroidAutoContextualPlaybackTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Issue #43, AA-07: a song picked in Android Auto starts inside its album, playlist, favorites or
 * random list (the queue is the whole list, the current item is the picked song), Play All starts at
 * the first song and favorites are not cut at the 100 rows Android Auto displays.
 *
 * The assertions are made on what Media3 receives: `onSetMediaItems` returns the queue and the
 * start index the session hands to the player. No network and no sleeps: fake service, inline IO.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoContextualPlaybackTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val session: MediaSession = mock()
    // Taki's own controller: only it may supply a playback source uri (#46); media ids work for any.
    private val controller: MediaSession.ControllerInfo = mock<MediaSession.ControllerInfo>().also {
        whenever(it.uid).thenReturn(android.os.Process.myUid())
    }
    private val library: MediaLibraryService.MediaLibrarySession = mock()
    private val service: MusicService = mock()
    private lateinit var callback: MediaLibrarySessionCallback
    private var serverId = 1

    private val albums = mutableMapOf<String, List<String>>()
    private val playlists = mutableMapOf<Pair<Int, String>, List<String>>()
    private var favorites = listOf<String>()
    private var randoms = listOf<String>()
    private var randomRequests = 0

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
            directory(playlists[serverId to inv.getArgument<String>(0)])
        }
        wheneverBlocking { service.getAlbumAsDir(any(), anyOrNull(), any()) }.thenAnswer { inv ->
            directory(albums[inv.getArgument<String>(0)])
        }
        wheneverBlocking { service.getMusicDirectory(any(), anyOrNull(), any()) }.thenAnswer { inv ->
            directory(albums[inv.getArgument<String>(0)])
        }
        wheneverBlocking { service.getStarred2() }.thenAnswer { SearchResult(songs = favorites.map(::track)) }
        wheneverBlocking { service.getStarred() }.thenAnswer { SearchResult(songs = favorites.map(::track)) }
        wheneverBlocking { service.getRandomSongs(any()) }.thenAnswer {
            randomRequests++
            directory(randoms)
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
        if (ids == null) throw IllegalStateException("not found")
        return MusicDirectory().apply { ids.forEach { add(track(it)) } }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun legacy(mediaId: String) =
        mutableListOf(buildMediaItem(title = mediaId, mediaId = mediaId, isPlayable = false))

    /** Starts playback of a media id as Android Auto does and returns what the player is given. */
    private fun start(mediaId: String): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = callback.onSetMediaItems(session, controller, legacy(mediaId), C.INDEX_UNSET, C.TIME_UNSET)
        idle()
        return future
    }

    private fun MediaSession.MediaItemsWithStartPosition.ids() = mediaItems.map { it.mediaId }

    private fun ListenableFuture<*>.failure(): Throwable =
        try {
            get()
            throw AssertionError("expected a failed future")
        } catch (e: ExecutionException) {
            e.cause!!
        }

    private fun ids(prefix: String, count: Int) = (1..count).map { "$prefix$it" }

    private fun albumSong(album: String, song: String) = "MEDIA_ALBUM_SONG_ITEM|$album|name|$song"
    private fun playlistSong(list: String, song: String, at: Int? = null) =
        "MEDIA_PLAYLIST_SONG_ITEM|$list|name-$list|$song" + (at?.let { "|$it" } ?: "")

    // ---- AA07-01..05: a song starts inside its list at its own position ----

    @Test
    fun `an album song is queued with its album and starts at its own index`() {
        albums["alb"] = listOf("A", "B", "C", "D", "E")
        val result = start(albumSong("alb", "C")).get()
        assertEquals(listOf("A", "B", "C", "D", "E"), result.ids())
        assertEquals(2, result.startIndex)
        assertEquals("the song starts from its beginning", C.TIME_UNSET, result.startPositionMs)
    }

    @Test
    fun `a playlist song is queued with its playlist and starts at its own index`() {
        playlists[1 to "P"] = listOf("P1", "P2", "P3", "P4")
        val result = start(playlistSong("P", "P2", at = 1)).get()
        assertEquals(listOf("P1", "P2", "P3", "P4"), result.ids())
        assertEquals(1, result.startIndex)
    }

    @Test
    fun `the first and the last song start at index zero and at the last index`() {
        albums["alb"] = listOf("A", "B", "C")
        assertEquals(0, start(albumSong("alb", "A")).get().startIndex)
        assertEquals(2, start(albumSong("alb", "C")).get().startIndex)
    }

    @Test
    fun `an album with one song queues just that song`() {
        albums["one"] = listOf("S")
        val result = start(albumSong("one", "S")).get()
        assertEquals(listOf("S"), result.ids())
        assertEquals(0, result.startIndex)
    }

    // ---- AA07-08/09: Play All ----

    @Test
    fun `play all starts at the first song with the collection order unchanged`() {
        playlists[1 to "O"] = listOf("O3", "O1", "O2")
        val result = start("MEDIA_PLAYLIST_ITEM|O|name-O").get()
        assertEquals(listOf("O3", "O1", "O2"), result.ids())
        assertEquals(0, result.startIndex)
    }

    @Test
    fun `an empty collection fails instead of playing something else`() {
        playlists[1 to "E"] = emptyList()
        playlists[1 to "P"] = listOf("P1")
        start("MEDIA_PLAYLIST_ITEM|P|name-P").get()
        assertTrue(start("MEDIA_PLAYLIST_ITEM|E|name-E").failure() is IllegalStateException)
    }

    // ---- AA07-12..15: favorites are not cut at what is displayed ----

    @Test
    fun `favorites play all queues every favorite, beyond the displayed rows`() {
        listOf(1, 100, 101, 500, 1000).forEach { size ->
            favorites = ids("F", size)
            callback.onGetChildren(library, controller, "MEDIA_SONG_STARRED_ID", 0, Int.MAX_VALUE, null)
            idle()
            val result = start("MEDIA_SONG_STARRED_ID").get()
            assertEquals("queue for $size favorites", favorites, result.ids())
            assertEquals(0, result.startIndex)
        }
    }

    @Test
    fun `the favorites list still shows only the first hundred rows`() {
        favorites = ids("F", 150)
        val rows = callback.onGetChildren(library, controller, "MEDIA_SONG_STARRED_ID", 0, Int.MAX_VALUE, null)
        idle()
        // 100 songs plus the Play All row
        assertEquals(101, rows.get().value!!.size)
    }

    @Test
    fun `a tapped favorite keeps the favorites as its queue`() {
        favorites = ids("F", 150)
        callback.onGetChildren(library, controller, "MEDIA_SONG_STARRED_ID", 0, Int.MAX_VALUE, null)
        idle()
        val result = start("MEDIA_SONG_STARRED_ITEM|F40").get()
        assertEquals(favorites, result.ids())
        assertEquals(39, result.startIndex)
    }

    // ---- AA07-18: the same song twice in a playlist ----

    @Test
    fun `the tapped occurrence of a song that a playlist holds twice is the one that starts`() {
        playlists[1 to "D"] = listOf("X", "Y", "X", "Z")
        assertEquals(0, start(playlistSong("D", "X", at = 0)).get().startIndex)
        assertEquals(2, start(playlistSong("D", "X", at = 2)).get().startIndex)
        // an id without a position (older client) falls back to the first occurrence
        assertEquals(0, start(playlistSong("D", "X")).get().startIndex)
        // and a position that no longer points at the song is not trusted
        assertEquals(0, start(playlistSong("D", "X", at = 3)).get().startIndex)
    }

    @Test
    fun `playlist rows carry their position so repeated songs have distinct ids`() {
        playlists[1 to "D"] = listOf("X", "Y", "X")
        val rows = callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|D|name-D", 0, Int.MAX_VALUE, null)
        idle()
        val songRows = rows.get().value!!.map { it.mediaId }.filter { it.startsWith("MEDIA_PLAYLIST_SONG_ITEM") }
        assertEquals(songRows.size, songRows.toSet().size)
    }

    // ---- AA07-19..23: identity, concurrency, snapshots ----

    @Test
    fun `a song is never taken from another playlist or another server`() {
        playlists[1 to "A"] = listOf("A1", "A2")
        playlists[1 to "B"] = listOf("B1", "B2", "B3")
        assertEquals(listOf("B1", "B2", "B3"), start(playlistSong("B", "B2", at = 1)).get().ids())
        assertTrue(start(playlistSong("B", "A2", at = 1)).failure() is IllegalStateException)
        playlists[2 to "A"] = listOf("S1", "S2")
        serverId = 2
        assertEquals(listOf("S1", "S2"), start(playlistSong("A", "S2", at = 1)).get().ids())
    }

    @Test
    fun `a result that arrives after the server changed is rejected`() {
        val gate = CompletableDeferred<List<Track>>()
        callback.mediaIdResolver = { gate.await() }
        val future = callback.onSetMediaItems(session, controller, legacy(albumSong("a", "x")), C.INDEX_UNSET, C.TIME_UNSET)
        idle()
        serverId = 2
        gate.complete(listOf(track("x")))
        idle()
        assertTrue(future.failure() is IllegalStateException)
    }

    @Test
    fun `the newest request wins and an older one can not replace its queue`() {
        val slow = CompletableDeferred<List<Track>>()
        callback.mediaIdResolver = { id -> if (id.endsWith("slow")) slow.await() else listOf(track("a"), track("b")) }
        val older = callback.onSetMediaItems(session, controller, legacy(albumSong("a", "slow")), C.INDEX_UNSET, C.TIME_UNSET)
        idle()
        val newer = start(albumSong("a", "b"))
        slow.complete(listOf(track("slow")))
        idle()
        assertTrue(older.isCancelled)
        assertEquals(listOf("a", "b"), newer.get().ids())
        assertEquals(1, newer.get().startIndex)
    }

    @Test
    fun `a random song starts in the list that was shown and asks for no new one`() {
        randoms = ids("R", 6)
        callback.onGetChildren(library, controller, "MEDIA_SONG_RANDOM_ID", 0, Int.MAX_VALUE, null)
        idle()
        randoms = ids("N", 6) // the server would now answer differently
        val result = start("MEDIA_SONG_RANDOM_ITEM|R4").get()
        assertEquals(ids("R", 6), result.ids())
        assertEquals(3, result.startIndex)
        assertEquals(1, randomRequests)
    }

    // ---- AA07-26 and compatibility ----

    @Test
    fun `a search result plays as a single song`() {
        callback.mediaIdResolver = { listOf(track("s1")) }
        val result = start("MEDIA_SEARCH_SONG_ITEM|s1").get()
        assertEquals(listOf("s1"), result.ids())
        assertEquals(0, result.startIndex)
    }

    @Test
    fun `items a controller built itself keep the start it asked for`() {
        val item = MediaItem.Builder()
            .setMediaId("m1")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse("https://example.org/1")).build()
            )
            .build()
        val future = callback.onSetMediaItems(session, controller, mutableListOf(item, item), 1, 4_000L)
        idle()
        val result = future.get()
        assertEquals(1, result.startIndex)
        assertEquals(4_000L, result.startPositionMs)
    }

    @Test
    fun `appending a song adds only that song, not its list`() {
        albums["alb"] = listOf("A", "B", "C")
        val future = callback.onAddMediaItems(session, controller, legacy(albumSong("alb", "B")))
        idle()
        assertEquals(listOf("B"), future.get().map { it.mediaId })
    }

    @Test
    fun `describing a song answers with that song, not the first of its list`() {
        albums["alb"] = listOf("A", "B", "C")
        val item = callback.onGetItem(library, controller, albumSong("alb", "C"))
        idle()
        assertEquals("C", item.get().value!!.mediaId)
    }

    @Test
    fun `playing a song makes no request when its list was already shown`() {
        playlists[1 to "A"] = listOf("A1", "A2", "A3")
        callback.onGetChildren(library, controller, "MEDIA_PLAYLIST_ITEM|A|name-A", 0, Int.MAX_VALUE, null)
        idle()
        start(playlistSong("A", "A2", at = 1)).get()
        verifyBlocking(service) { getPlaylist("A", "name-A") } // the browse only
        verifyBlocking(service, never()) { getRandomSongs(any()) }
    }

    @Test
    fun `the selected index is found by position, then by id`() {
        val tracks = listOf(Track(id = "x"), Track(id = "y"), Track(id = "x"))
        assertEquals(2, selectedIndexOf("MEDIA_PLAYLIST_SONG_ITEM|p|n|x|2", tracks))
        assertEquals(0, selectedIndexOf("MEDIA_PLAYLIST_SONG_ITEM|p|n|x", tracks))
        assertEquals(1, selectedIndexOf("MEDIA_ALBUM_SONG_ITEM|a|n|y", tracks))
        assertEquals(NOT_FOUND, selectedIndexOf("MEDIA_ALBUM_SONG_ITEM|a|n|q", tracks))
        assertEquals(0, selectedIndexOf("MEDIA_PLAYLIST_ITEM|p|n", tracks))
        assertFalse(isSongSelection("MEDIA_SEARCH_SONG_ITEM|y"))
    }
}
