/*
 * AlbumPlayDeterministicTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerManager.InsertionMode
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * "Album > Play" must be deterministic: it replaces the queue with the album, starts at track 0
 * with shuffle OFF, and plays -- regardless of any shuffle mode a previously played queue left
 * enabled. "Album > Shuffle" is the only album action that enables shuffle.
 *
 * Root cause this guards: [MediaPlayerManager.addToPlaylistLocked] only ever turned shuffle *on*
 * (`if (shuffle) isShufflePlayEnabled = true`) and never off, so a queue-replacing Play inherited
 * shuffle from the previous queue and `startPlaybackAt()` then started at a random shuffled
 * window instead of index 0.
 *
 * These drive [MediaPlayerManager.addToPlaylist] with exactly the arguments
 * `TrackCollectionFragment.playAll()` uses for the album hero's Play / Shuffle buttons
 * (`InsertionMode.CLEAR`, `autoPlay = true`, `shuffle = false | true`), against a
 * [FakeMedia3Player] with a real timeline.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class AlbumPlayDeterministicTest {

    private lateinit var manager: MediaPlayerManager
    private lateinit var player: FakeMedia3Player

    private val album = (1..4).map { n ->
        Track(id = "t$n", title = "Song $n", artist = "Artist", album = "Album", isDirectory = false)
    }

    @Before
    fun setUp() {
        RobolectricUAppContext.install()

        // Make Track.toMediaItem() a pure cache hit so the queue rebuild never reaches
        // FileUtil/Settings (unloadable in this module's resource-less unit-test setup).
        album.forEach { track ->
            MediaItemConverter.addToCache(
                track.id,
                buildMediaItem(title = track.title!!, mediaId = track.id, isPlayable = true)
            )
        }

        manager = MediaPlayerManager(mock(), mock())
        player = FakeMedia3Player(Looper.getMainLooper())
        manager.setPrivateField("controller", player)
    }

    @After
    fun tearDown() {
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun pumpUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Exactly what TrackCollectionFragment.playAll(shuffle) issues for the album hero buttons. */
    private fun albumPlay(shuffle: Boolean) {
        manager.addToPlaylist(
            songs = album,
            autoPlay = true,
            shuffle = shuffle,
            insertionMode = InsertionMode.CLEAR
        )
    }

    @Test
    fun `shuffle previously ON then Album Play turns shuffle OFF and starts at index 0`() {
        manager.isShufflePlayEnabled = true
        assertTrue("precondition: shuffle inherited from a previous queue", manager.isShufflePlayEnabled)

        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }

        assertFalse("Album Play must clear inherited shuffle", manager.isShufflePlayEnabled)
        assertFalse("Media3 player shuffle must be off", player.shuffleModeEnabled)
        assertEquals("playback must start at track 0", 0, player.currentMediaItemIndex)
        assertTrue("playback must have started", player.playWhenReady)
    }

    @Test
    fun `Album Play with shuffle already OFF starts at index 0`() {
        assertFalse("precondition: shuffle off", manager.isShufflePlayEnabled)

        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }

        assertFalse(manager.isShufflePlayEnabled)
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals("queue is the album", album.map { it.id }, playerIds())
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `explicit Album Shuffle enables shuffle over the album queue`() {
        assertFalse(manager.isShufflePlayEnabled)

        albumPlay(shuffle = true)
        pumpUntil { player.mediaItemCount == album.size && manager.isShufflePlayEnabled }

        assertTrue("Album Shuffle must enable shuffle", manager.isShufflePlayEnabled)
        assertTrue(player.shuffleModeEnabled)
        assertEquals("queue is the album", album.map { it.id }, playerIds())
    }

    @Test
    fun `re-Play of the already-loaded album still clears a shuffle enabled from Now Playing`() {
        // Load the album once (sequential), then the user enables shuffle from Now Playing.
        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }
        manager.isShufflePlayEnabled = true
        assertTrue(manager.isShufflePlayEnabled)

        // Pressing Play on the same album again takes addToPlaylistLocked's "queue already
        // matches" fast path (no rebuild); the shuffle-clearing guard must still apply there.
        albumPlay(shuffle = false)
        pumpUntil { !manager.isShufflePlayEnabled }

        assertFalse("fast-path Album Play must also clear shuffle", manager.isShufflePlayEnabled)
        assertFalse(player.shuffleModeEnabled)
    }

    private fun playerIds(): List<String> =
        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
}
