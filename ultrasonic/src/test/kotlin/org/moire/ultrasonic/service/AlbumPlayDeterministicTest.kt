/*
 * AlbumPlayDeterministicTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import io.reactivex.rxjava3.disposables.Disposable
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Contract for the album hero's Play / Shuffle buttons, driven through
 * [MediaPlayerManager.addToPlaylist] with exactly the arguments `TrackCollectionFragment.playAll()`
 * uses (`InsertionMode.CLEAR`, `autoPlay = true`, `shuffle = false | true`), against a
 * [FakeMedia3Player] with a real timeline.
 *
 * ```
 * Album Play    = album queue + shuffle OFF + start at index 0
 * Album Shuffle = album queue + shuffle ON  + start at the shuffled order's first window
 *                 (random), NOT forced to index 0
 * ```
 *
 * Two root causes are guarded here:
 *  - `addToPlaylistLocked()` only ever turned shuffle *on* and never off, so a queue-replacing
 *    Play inherited shuffle from the previous queue (fixed: [MediaPlayerManager] clears it).
 *  - a queue-replacing Shuffle enabled shuffle with `RxBus.ShufflePlay(reshuffleAll = false)`,
 *    so PlaybackService pinned the fresh queue's track 0 as the shuffle order's first window and
 *    playback always started at track 1 (fixed: [MediaPlayerManager] sends `reshuffleAll = true`
 *    for `InsertionMode.CLEAR`). The order maths itself is covered by [ShuffleOrderTest].
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class AlbumPlayDeterministicTest {

    private lateinit var manager: MediaPlayerManager
    private lateinit var player: FakeMedia3Player
    private lateinit var shuffleEvents: MutableList<RxBus.ShufflePlay>
    private lateinit var shuffleSubscription: Disposable

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

        shuffleEvents = Collections.synchronizedList(mutableListOf())
        shuffleSubscription = RxBus.shufflePlayObservable.subscribe { shuffleEvents.add(it) }

        manager = MediaPlayerManager(mock(), mock())
        player = FakeMedia3Player(Looper.getMainLooper())
        manager.setPrivateField("controller", player)
    }

    @After
    fun tearDown() {
        shuffleSubscription.dispose()
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

    private fun deferredPlay(): Any? = manager.getPrivateField("deferredPlay")

    private fun shuffleEventsSnapshot(): List<RxBus.ShufflePlay> =
        synchronized(shuffleEvents) { shuffleEvents.toList() }

    // --- A. Album Play with previous shuffle ON -------------------------------------------------

    @Test
    fun `shuffle previously ON then Album Play turns shuffle OFF and starts at index 0`() {
        manager.isShufflePlayEnabled = true
        assertTrue("precondition: shuffle inherited from a previous queue", manager.isShufflePlayEnabled)
        synchronized(shuffleEvents) { shuffleEvents.clear() }

        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }

        assertFalse("Album Play must clear inherited shuffle", manager.isShufflePlayEnabled)
        assertFalse("Media3 player shuffle must be off", player.shuffleModeEnabled)
        assertEquals("playback must start at track 0", 0, player.currentMediaItemIndex)
        assertTrue("playback must have started", player.playWhenReady)
        assertNull("Album Play must not arm a deferred shuffle start", deferredPlay())
        assertTrue(
            "Album Play must not request a reshuffle, saw ${shuffleEventsSnapshot()}",
            shuffleEventsSnapshot().none { it.reshuffleAll }
        )
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
        assertNull(deferredPlay())
    }

    // --- B. Album Shuffle --------------------------------------------------------------------------

    @Test
    fun `Album Shuffle enables shuffle and starts from the shuffled order, not index 0`() {
        assertFalse(manager.isShufflePlayEnabled)

        albumPlay(shuffle = true)
        pumpUntil { player.mediaItemCount == album.size && manager.isShufflePlayEnabled }

        assertTrue("Album Shuffle must enable shuffle", manager.isShufflePlayEnabled)
        assertTrue(player.shuffleModeEnabled)
        assertEquals("queue is the album", album.map { it.id }, playerIds())

        // Start is deferred to currentTimeline.getFirstWindowIndex(shuffle) - the shuffled
        // order's first window - rather than the `else { play(0) }` branch.
        assertNotNull("Album Shuffle must arm the shuffled-order start", deferredPlay())
        assertFalse("playback must not be force-started at index 0", player.playWhenReady)

        val last = shuffleEventsSnapshot().last()
        assertEquals(
            "Album Shuffle must ask PlaybackService for a full reshuffle",
            RxBus.ShufflePlay(enabled = true, reshuffleAll = true),
            last
        )
    }

    // --- C. Replaying an already-loaded album with Album Shuffle --------------------------------

    @Test
    fun `re-Shuffle of an already-loaded album uses shuffle semantics, not the sequential fast path`() {
        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }
        player.seekTo(1, 0L)
        player.pause()
        synchronized(shuffleEvents) { shuffleEvents.clear() }

        // Same track list already loaded: the `!shuffle && queueAlreadyMatches` fast path (which
        // would just seek within the sequential queue) must NOT be taken, because shuffle = true.
        albumPlay(shuffle = true)
        pumpUntil { manager.isShufflePlayEnabled && deferredPlay() != null }

        assertTrue(manager.isShufflePlayEnabled)
        assertTrue(player.shuffleModeEnabled)
        assertNotNull("re-Shuffle must arm the shuffled-order start, not seek sequentially", deferredPlay())
        assertEquals(
            "re-Shuffle must request a full reshuffle",
            RxBus.ShufflePlay(enabled = true, reshuffleAll = true),
            shuffleEventsSnapshot().last()
        )
    }

    // --- D. Now Playing shuffle toggle is unchanged -------------------------------------------------

    @Test
    fun `Now Playing shuffle toggle keeps the current track pinned - reshuffleAll stays false`() {
        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }
        player.seekTo(2, 0L)
        synchronized(shuffleEvents) { shuffleEvents.clear() }

        val enabled = manager.toggleShuffle()
        assertTrue("toggle enables shuffle", enabled)
        assertTrue(manager.isShufflePlayEnabled)

        manager.toggleShuffle()
        assertFalse(manager.isShufflePlayEnabled)

        assertEquals(
            "toggle must emit exactly on/off, both with reshuffleAll = false",
            listOf(
                RxBus.ShufflePlay(enabled = true, reshuffleAll = false),
                RxBus.ShufflePlay(enabled = false, reshuffleAll = false)
            ),
            shuffleEventsSnapshot()
        )
        // A mid-playback toggle never re-seeks the player.
        assertEquals(2, player.currentMediaItemIndex)
    }

    // --- Album Play on the *same* album is still an explicit command, never a no-op -----------

    @Test
    fun `Album Play after Album Shuffle on the same album returns to index 0 and keeps playing`() {
        // Start the album shuffled, then get it into "playing, shuffle ON, at a later track".
        albumPlay(shuffle = true)
        pumpUntil { player.mediaItemCount == album.size && manager.isShufflePlayEnabled }
        player.seekTo(2, 0L)
        player.play()
        pumpUntil { player.isPlaying }
        assertTrue("precondition: playing", player.isPlaying)
        assertTrue("precondition: shuffle ON", manager.isShufflePlayEnabled)
        assertEquals("precondition: not at index 0", 2, player.currentMediaItemIndex)

        // Album Play on the SAME, already-loaded album.
        albumPlay(shuffle = false)
        pumpUntil { player.currentMediaItemIndex == 0 && !manager.isShufflePlayEnabled }

        assertFalse("Album Play must clear shuffle", manager.isShufflePlayEnabled)
        assertFalse(player.shuffleModeEnabled)
        assertEquals("Album Play must move to index 0", 0, player.currentMediaItemIndex)
        assertTrue("Album Play must keep playback running", player.isPlaying)
    }

    @Test
    fun `Album Play on the same album already playing at a later track returns to index 0`() {
        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }
        player.seekTo(2, 0L)
        assertTrue("precondition: playing", player.isPlaying)
        assertEquals(2, player.currentMediaItemIndex)

        albumPlay(shuffle = false)
        pumpUntil { player.currentMediaItemIndex == 0 }

        assertEquals("Album Play must return to index 0", 0, player.currentMediaItemIndex)
        assertTrue("playback continues", player.isPlaying)
        assertFalse(manager.isShufflePlayEnabled)
    }

    @Test
    fun `Album Play on the same album paused at a later track returns to index 0 and starts`() {
        albumPlay(shuffle = false)
        pumpUntil { player.mediaItemCount == album.size && player.playWhenReady }
        player.seekTo(3, 0L)
        player.pause()
        assertFalse("precondition: paused", player.isPlaying)
        assertEquals(3, player.currentMediaItemIndex)

        albumPlay(shuffle = false)
        pumpUntil { player.currentMediaItemIndex == 0 && player.playWhenReady }

        assertEquals("Album Play must return to index 0", 0, player.currentMediaItemIndex)
        assertTrue("Album Play must start playback", player.isPlaying)
        assertFalse(manager.isShufflePlayEnabled)
    }

    private fun playerIds(): List<String> =
        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
}
