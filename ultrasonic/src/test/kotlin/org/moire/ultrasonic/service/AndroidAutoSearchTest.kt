/*
 * AndroidAutoSearchTest.kt
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
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Issue #43, AA-09: voice play-from-search and the Media3 browse-search callbacks.
 *
 * Voice (`requestMetadata.searchQuery`) resolves exactly one song or album from the active server and
 * fails in a controlled way (never `NoSuchElementException`, never an unrelated song) when there is no
 * match, the query is blank, the server fails, the request times out, the server changes or a newer
 * request supersedes it. A search-result row is identified by its own song id (resolved without any
 * "latest search" state), so a later search can not substitute another song.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoSearchTest {

    private val lifecycleSupport: MediaPlayerLifecycleSupport = mock()
    private val session: MediaSession = mock()
    private val controller: MediaSession.ControllerInfo = mock()
    private val library: MediaLibraryService.MediaLibrarySession = mock()
    private val service: MusicService = mock()
    private lateinit var callback: MediaLibrarySessionCallback
    private var serverId = 1

    private val songs = mutableMapOf<String, List<Track>>() // query -> song results
    private val albums = mutableMapOf<String, List<Album>>() // query -> album results
    private val albumTracks = mutableMapOf<String, List<String>>() // album id -> track ids
    private val byId = mutableMapOf<String, Track>()
    private val queries = mutableListOf<String>()

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

        wheneverBlocking { service.search(any()) }.thenAnswer { inv ->
            val criteria = inv.getArgument<SearchCriteria>(0)
            queries += criteria.query
            SearchResult(
                albums = albums[criteria.query].orEmpty(),
                songs = songs[criteria.query].orEmpty()
            )
        }
        wheneverBlocking { service.getSong(any()) }.thenAnswer { byId[it.getArgument<String>(0)] }
        wheneverBlocking { service.getAlbumAsDir(any(), anyOrNull(), any()) }.thenAnswer { inv ->
            directory(albumTracks[inv.getArgument<String>(0)])
        }
        wheneverBlocking { service.getMusicDirectory(any(), anyOrNull(), any()) }.thenAnswer { inv ->
            directory(albumTracks[inv.getArgument<String>(0)])
        }
    }

    @After
    fun tearDown() {
        callback.release()
        stopKoin()
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun track(id: String, title: String = "Title $id", artist: String = "Artist"): Track {
        MediaItemConverter.addToCache(id, buildMediaItem(title = title, mediaId = id, isPlayable = true))
        return Track(id = id, title = title, artist = artist).also { byId[id] = it }
    }

    private fun album(id: String, name: String) = Album(id = id, title = name, name = name)

    private fun directory(ids: List<String>?): MusicDirectory {
        if (ids == null) throw IllegalStateException("not found")
        return MusicDirectory().apply { ids.forEach { add(track(it)) } }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun voice(query: String): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val item = MediaItem.Builder()
            .setMediaId("search")
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query).build())
            .build()
        val future = callback.onSetMediaItems(session, controller, mutableListOf(item), C.INDEX_UNSET, C.TIME_UNSET)
        idle()
        return future
    }

    private fun select(mediaId: String): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = callback.onSetMediaItems(
            session,
            controller,
            mutableListOf(buildMediaItem(title = mediaId, mediaId = mediaId, isPlayable = false)),
            C.INDEX_UNSET,
            C.TIME_UNSET
        )
        idle()
        return future
    }

    private fun ListenableFuture<MediaSession.MediaItemsWithStartPosition>.ids() =
        get(WAIT_S, TimeUnit.SECONDS).mediaItems.map { it.mediaId }

    private fun ListenableFuture<*>.failure(): Throwable =
        try {
            get(WAIT_S, TimeUnit.SECONDS)
            throw AssertionError("expected a failed future")
        } catch (e: ExecutionException) {
            e.cause!!
        }

    // ---- voice play-from-search ----

    @Test
    fun `a single matching song is resolved`() {
        songs["roses"] = listOf(track("s1", "Roses"))
        assertEquals(listOf("s1"), voice("roses").ids())
    }

    @Test
    fun `the play prefix is stripped from the query`() {
        songs["roses"] = listOf(track("s1", "Roses"))
        assertEquals(listOf("s1"), voice("play roses").ids())
        assertEquals(listOf("roses"), queries)
    }

    @Test
    fun `an exact title wins over the server ranking, otherwise the top ranked song is played`() {
        songs["roses"] = listOf(track("s1", "Roses (Live)"), track("s2", "Roses"), track("s3", "Roses"))
        assertEquals("exact title, first in server order", listOf("s2"), voice("roses").ids())

        songs["blue"] = listOf(track("b1", "Blue Moon"), track("b2", "Blue Sky"))
        assertEquals("no exact title: the server's first", listOf("b1"), voice("blue").ids())
    }

    @Test
    fun `duplicate titles by different artists keep their own ids`() {
        songs["roses"] = listOf(track("a", "Roses", "Outkast"), track("b", "Roses", "Other"))
        assertEquals(listOf("a"), voice("roses").ids())
        assertEquals("B is still addressable by its own id", listOf("b"), select("MEDIA_SEARCH_SONG_ITEM|b").ids())
    }

    @Test
    fun `an exact album name plays that album when no song title matches exactly`() {
        songs["aztlan"] = listOf(track("s1", "Aztlan Blues"))
        albums["aztlan"] = listOf(album("alb", "Aztlan"))
        albumTracks["alb"] = listOf("t1", "t2", "t3")
        assertEquals(listOf("t1", "t2", "t3"), voice("aztlan").ids())
    }

    @Test
    fun `no match fails in a controlled way and plays nothing`() {
        val failure = voice("nothing at all").failure()
        assertTrue("was $failure", failure is IllegalStateException)
    }

    @Test
    fun `a blank query fails without asking the server`() {
        assertTrue(voice("   ").failure() is IllegalStateException)
        assertTrue(voice("play ").failure() is IllegalStateException)
        assertEquals(emptyList<String>(), queries)
    }

    @Test
    fun `a server error fails in a controlled way`() {
        wheneverBlocking { service.search(any()) }.thenThrow(IllegalStateException("server down"))
        assertTrue(voice("roses").failure() is IllegalStateException)
    }

    @Test
    fun `a search that never answers fails after the timeout`() {
        callback.resolutionTimeoutMs = 30_000
        wheneverBlocking { service.search(any()) }.doSuspendableAnswer { CompletableDeferred<SearchResult>().await() }
        val future = voice("roses")
        assertFalse(future.isDone)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(30_100))
        assertTrue(future.failure() is IllegalStateException)
    }

    @Test
    fun `a result that arrives after the server changed is rejected`() {
        val gate = CompletableDeferred<SearchResult>()
        wheneverBlocking { service.search(any()) }.doSuspendableAnswer { gate.await() }
        val future = voice("roses")
        serverId = 2
        gate.complete(SearchResult(songs = listOf(track("s1", "Roses"))))
        idle()
        assertTrue(future.failure() is IllegalStateException)
    }

    @Test
    fun `a newer search supersedes an older one that answers late`() {
        val slow = CompletableDeferred<SearchResult>()
        wheneverBlocking { service.search(any()) }.doSuspendableAnswer { inv ->
            if (inv.getArgument<SearchCriteria>(0).query == "old") slow.await()
            else SearchResult(songs = listOf(track("new1", "New")))
        }
        val older = voice("old")
        val newer = voice("new")
        slow.complete(SearchResult(songs = listOf(track("old1", "Old"))))
        idle()
        assertTrue(older.isCancelled)
        assertEquals(listOf("new1"), newer.ids())
    }

    @Test
    fun `a voice request does not start the player when it fails`() {
        voice("nothing").failure()
        // the infrastructure may start (a request is a playback request) but nothing was queued
        verify(session, never()).player
    }

    // ---- search results are identified by their own id ----

    @Test
    fun `a result from an earlier search can still be played after a later search`() {
        songs["one"] = listOf(track("s1", "One"))
        songs["two"] = listOf(track("s2", "Two"))
        callback.onSearch(library, controller, "one", null)
        callback.onSearch(library, controller, "two", null)
        idle()
        assertEquals(listOf("s1"), select("MEDIA_SEARCH_SONG_ITEM|s1").ids())
        assertEquals(listOf("s2"), select("MEDIA_SEARCH_SONG_ITEM|s2").ids())
    }

    @Test
    fun `a result row is resolved from the server when nothing is remembered`() {
        track("s9", "Nine")
        assertEquals(listOf("s9"), select("MEDIA_SEARCH_SONG_ITEM|s9").ids())
    }

    @Test
    fun `a result that no longer exists fails instead of playing something else`() {
        songs["one"] = listOf(track("s1", "One"))
        byId.remove("s1")
        assertTrue(select("MEDIA_SEARCH_SONG_ITEM|gone").failure() is IllegalStateException)
    }

    @Test
    fun `a result is never taken from another server`() {
        songs["one"] = listOf(track("s1", "One"))
        callback.onSearch(library, controller, "one", null)
        idle()
        serverId = 2
        byId.remove("s1") // server 2 does not know this song
        assertTrue(select("MEDIA_SEARCH_SONG_ITEM|s1").failure() is IllegalStateException)
    }

    @Test
    fun `a malformed search media id is rejected`() {
        assertTrue(select("MEDIA_SEARCH_SONG_ITEM").failure() is IllegalArgumentException)
    }

    // ---- Media3 browse search ----

    @Test
    fun `onSearch searches, notifies the count and starts no playback`() {
        songs["roses"] = listOf(track("s1", "Roses"), track("s2", "Roses 2"))
        albums["roses"] = listOf(album("alb", "Roses"))

        val result = callback.onSearch(library, controller, "roses", null)
        idle()

        assertEquals(LibraryResult.RESULT_SUCCESS, result.get(WAIT_S, TimeUnit.SECONDS).resultCode)
        verify(library).notifySearchResultChanged(eq(controller), eq("roses"), eq(3), anyOrNull())
        verify(lifecycleSupport, never()).ensureCreatedForPlayback(anyOrNull())
    }

    @Test
    fun `getSearchResult returns songs then albums in server order, paged`() {
        songs["roses"] = listOf(track("s1", "Roses"), track("s2", "Roses 2"))
        albums["roses"] = listOf(album("alb", "Roses"))
        callback.onSearch(library, controller, "roses", null)
        idle()

        val all = callback.onGetSearchResult(library, controller, "roses", 0, 10, null)
        idle()
        assertEquals(
            listOf("MEDIA_SEARCH_SONG_ITEM|s1", "MEDIA_SEARCH_SONG_ITEM|s2", "MEDIA_ALBUM_ITEM|alb|Roses"),
            all.get(WAIT_S, TimeUnit.SECONDS).value!!.map { it.mediaId }
        )

        val page = callback.onGetSearchResult(library, controller, "roses", 1, 2, null)
        idle()
        assertEquals(listOf("MEDIA_ALBUM_ITEM|alb|Roses"), page.get(WAIT_S, TimeUnit.SECONDS).value!!.map { it.mediaId })
    }

    @Test
    fun `results of one query are not returned for another`() {
        songs["one"] = listOf(track("s1", "One"))
        callback.onSearch(library, controller, "one", null)
        idle()
        val second = callback.onGetSearchResult(library, controller, "two", 0, 10, null)
        idle()
        assertEquals("a query without results is an empty success, not the previous results", 0, second.get(WAIT_S, TimeUnit.SECONDS).value!!.size)
    }

    @Test
    fun `a blank search is a bad value and a failing server is an error`() {
        val blank = callback.onSearch(library, controller, " ", null)
        idle()
        assertEquals(SessionError.ERROR_BAD_VALUE, blank.get(WAIT_S, TimeUnit.SECONDS).resultCode)
        wheneverBlocking { service.search(any()) }.thenThrow(IllegalStateException("down"))
        val failed = callback.onSearch(library, controller, "roses", null)
        idle()
        assertTrue(failed.get(WAIT_S, TimeUnit.SECONDS).resultCode != LibraryResult.RESULT_SUCCESS)
        verify(library, never()).notifySearchResultChanged(any(), any(), any(), anyOrNull())
    }
}

private const val WAIT_S = 2L
