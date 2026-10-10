/*
 * ArtistRadioQueueBuilderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track

/**
 * #21: Artist Radio's data access must never run on the caller (Main) thread. The fake service
 * behaves like `CachedMusicService` over Room: any call made on the thread that invoked `build`
 * throws `IllegalStateException`, exactly as Room's main-thread check did live - and `fetch()`
 * swallows it, silently dropping the artist's albums from the queue.
 */
@RunWith(RobolectricTestRunner::class)
class ArtistRadioQueueBuilderTest {

    private val ioExecutor = Executors.newSingleThreadExecutor { Thread(it, "radio-io-test") }
    private val callerThread: Thread = Thread.currentThread()
    private val calledOn = java.util.Collections.synchronizedList(mutableListOf<String>())

    @After
    fun shutdown() {
        ioExecutor.shutdownNow()
    }

    private fun track(id: String, artist: String, artistId: String, album: String) =
        Track(id = id, title = id, artist = artist, artistId = artistId, album = album, albumId = album)

    private fun directory(vararg tracks: Track) = MusicDirectory().apply { addAll(tracks) }

    private fun guard(name: String) {
        calledOn += "$name@${Thread.currentThread().name}"
        check(Thread.currentThread() !== callerThread) { "Cannot access database on the main thread ($name)" }
    }

    private fun service(): MusicService = mock<MusicService>().also { svc ->
        svc.stub {
            onBlocking { getArtistInfo(any()) } doAnswer { guard("getArtistInfo"); null }
            onBlocking { getAlbumsOfArtist(any(), any(), any()) } doAnswer {
                guard("getAlbumsOfArtist")
                listOf(Album(id = "a1", title = "Album A1"), Album(id = "a2", title = "Album A2"))
            }
            onBlocking { getTopSongs(any(), any()) } doAnswer { guard("getTopSongs"); emptyList<Track>() }
            onBlocking { getAlbumAsDir(any(), any(), any()) } doAnswer {
                guard("getAlbumAsDir")
                val albumId = it.getArgument<String>(0)
                directory(*(1..8).map { n -> track("$albumId-t$n", "Artist A", "artistA", albumId) }.toTypedArray())
            }
            onBlocking { getRandomSongs(any()) } doAnswer {
                guard("getRandomSongs")
                directory(*(1..60).map { n -> track("rnd-$n", "Other $n", "other-$n", "rnd-$n") }.toTypedArray())
            }
        }
    }

    private fun build() = runBlocking {
        ArtistRadioQueueBuilder(service(), ioExecutor.asCoroutineDispatcher())
            .build("artistA", "Artist A")
    }

    @Test
    fun `all music service access happens off the caller thread`() {
        build()
        assertTrue(calledOn.isNotEmpty())
        assertTrue("not all on the IO thread: $calledOn", calledOn.all { it.contains("@radio-io-test") })
    }

    @Test
    fun `the artist's own album tracks are in the queue, not just random filler`() {
        val queue = build()
        val seed = queue.filter { it.artistId == "artistA" }
        assertTrue("seed tracks missing from $queue", seed.isNotEmpty())
        // Both albums were consulted (16 candidates), each track at most once
        assertTrue(seed.all { it.id.startsWith("a1-") || it.id.startsWith("a2-") })
        assertEquals(queue.size, queue.map { it.id }.distinct().size)
        assertEquals(ArtistRadioQueueBuilder.TARGET_SIZE, queue.size)
    }

    @Test
    fun `sanity - running on the caller thread reproduces the degraded queue (the pre-fix behaviour)`() {
        val queue = runBlocking {
            ArtistRadioQueueBuilder(service(), kotlinx.coroutines.Dispatchers.Unconfined)
                .build("artistA", "Artist A")
        }
        assertTrue("guard did not fire", calledOn.any { it.startsWith("getAlbumsOfArtist") })
        assertTrue("albums should have been lost", queue.none { it.artistId == "artistA" })
    }

    @Test
    fun `every production caller uses the builder's default IO boundary`() {
        val callers = java.io.File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.name != "ArtistRadioQueueBuilder.kt" }
            .flatMap { f -> f.readLines().filter { "ArtistRadioQueueBuilder(" in it }.map { f.name to it } }
            .toList()
        // Artist Detail (Compose host) and ContextMenuUtil (Artist List + legacy context menus)
        assertEquals(setOf("ArtistDetailFragment.kt", "ContextMenuUtil.kt"), callers.map { it.first }.toSet())
        assertTrue(callers.toString(), callers.none { (_, line) -> "Dispatchers" in line || "dispatcher" in line })
    }
}
