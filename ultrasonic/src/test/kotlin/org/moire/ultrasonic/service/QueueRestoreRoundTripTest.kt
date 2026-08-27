/*
 * QueueRestoreRoundTripTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import android.os.Environment
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.mock
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.buildMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * P0-2 -- queue restore round-trip.
 *
 * Protects the full persist -> deserialize -> [MediaPlayerManager.restore] path, with a current
 * item that is **not index 0**. The implementation carries history of a regression where a
 * restore of more than a handful of songs silently returned to track 0 (the seek raced the
 * asynchronous, chunked queue rebuild and hit an empty timeline), so the assertions are made
 * against the resulting player state, not against helpers in isolation.
 *
 * The player is a [FakeMedia3Player] so the timeline is real (the seek guards in
 * [MediaPlayerManager.seekTo] depend on `currentTimeline`), while [PlaybackStateSerializer] does
 * a genuine file round-trip through the Robolectric app cache dir.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class QueueRestoreRoundTripTest {

    private lateinit var serializer: PlaybackStateSerializer
    private lateinit var manager: MediaPlayerManager
    private lateinit var player: FakeMedia3Player

    private val queue = listOf("A", "B", "C", "D").map { id ->
        Track(id = id, title = "Title $id", artist = "Artist $id", isDirectory = false)
    }

    private var startedKoin = false

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/org.moire.ultrasonic.test"
        )

        // PlaybackStateSerializer resolves its Context through Koin; the production app graph is
        // never started in unit tests, so provide the one binding it needs.
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(
                    module {
                        single<Context> { ApplicationProvider.getApplicationContext() }
                    }
                )
            }
            startedKoin = true
        }

        // Track.toMediaItem() (used inside restore()) otherwise reaches FileUtil -> Storage ->
        // `object Settings`, whose <clinit> resolves ~30 R.string preference keys and throws in
        // this module's resource-less unit-test setup. Pre-seeding the converter cache makes
        // toMediaItem() a pure lookup, keeping this test about restore()'s queue/seek ordering.
        queue.forEach { track ->
            MediaItemConverter.addToCache(
                track.id,
                buildMediaItem(
                    title = track.title ?: track.id,
                    mediaId = track.id,
                    isPlayable = true,
                    artist = track.artist
                )
            )
        }

        serializer = PlaybackStateSerializer()
        manager = MediaPlayerManager(mock(), mock())
        player = FakeMedia3Player(Looper.getMainLooper())
        manager.setPrivateField("controller", player)
    }

    @After
    fun tearDown() {
        if (startedKoin) {
            stopKoin()
            startedKoin = false
        }
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun pumpUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun playerIds(): List<String> =
        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }

    @Test
    fun `persist then restore preserves order, current item, index, position, shuffle and repeat`() {
        serializer.serializeNow(
            tracks = queue,
            currentPlayingIndex = 2,
            currentPlayingPosition = 91_000,
            shufflePlay = true,
            repeatMode = Player.REPEAT_MODE_ALL
        )

        val restored = serializer.deserializeNow()!!
        manager.restore(restored, autoPlay = false)
        pumpUntil { player.mediaItemCount == 4 && player.currentMediaItemIndex == 2 }

        assertEquals("queue item order", listOf("A", "B", "C", "D"), playerIds())
        assertEquals("current item index", 2, player.currentMediaItemIndex)
        assertEquals("current media item", "C", player.currentMediaItem!!.mediaId)
        assertEquals("playback position", 91_000L, player.currentPosition)
        assertTrue("shuffle state", player.shuffleModeEnabled)
        assertEquals("repeat state", Player.REPEAT_MODE_ALL, player.repeatMode)
    }

    @Test
    fun `restore seeks to the persisted non-zero item even though the queue is rebuilt async`() {
        // The direct anti-regression check: a PlaybackState whose current index is deep in the
        // queue must never collapse back to track 0.
        val state = PlaybackState(
            songs = queue,
            currentPlayingIndex = 3,
            currentPlayingPosition = 5_000,
            shufflePlay = false,
            repeatMode = Player.REPEAT_MODE_OFF
        )

        manager.restore(state, autoPlay = false)
        pumpUntil { player.mediaItemCount == 4 }

        assertEquals(3, player.currentMediaItemIndex)
        assertEquals("D", player.currentMediaItem!!.mediaId)
        assertEquals(5_000L, player.currentPosition)
    }

    @Test
    fun `an empty persisted queue restores to an empty player without crashing`() {
        val state = PlaybackState(
            songs = emptyList(),
            currentPlayingIndex = -1,
            currentPlayingPosition = 0,
            shufflePlay = false,
            repeatMode = Player.REPEAT_MODE_OFF
        )

        manager.restore(state, autoPlay = false)
        pumpUntil(timeoutMs = 2_000) { false }

        assertEquals(0, player.mediaItemCount)
        assertTrue(player.currentTimeline.isEmpty)
    }

    @Test
    fun `a malformed current index does not seek to a bogus position`() {
        // 99 is well past the 4-track queue. seekTo(index, position)'s range guard must drop the
        // seek rather than throw or land somewhere arbitrary; the queue itself still restores.
        val state = PlaybackState(
            songs = queue,
            currentPlayingIndex = 99,
            currentPlayingPosition = 5_000,
            shufflePlay = false,
            repeatMode = Player.REPEAT_MODE_OFF
        )

        manager.restore(state, autoPlay = false)
        pumpUntil { player.mediaItemCount == 4 }

        assertEquals("queue item order", listOf("A", "B", "C", "D"), playerIds())
        assertEquals("index stays at the default rather than a bogus value", 0, player.currentMediaItemIndex)
    }

    @Test
    fun `restore after a fresh manager and player still lands on the persisted item`() {
        // Simulates session restore after service/process recreation: serialize once, then build
        // brand-new collaborators and restore into them.
        serializer.serializeNow(
            tracks = queue,
            currentPlayingIndex = 2,
            currentPlayingPosition = 40_000,
            shufflePlay = false,
            repeatMode = Player.REPEAT_MODE_ONE
        )

        val freshManager = MediaPlayerManager(mock(), mock())
        val freshPlayer = FakeMedia3Player(Looper.getMainLooper())
        freshManager.setPrivateField("controller", freshPlayer)

        freshManager.restore(serializer.deserializeNow()!!, autoPlay = false)
        pumpUntil { freshPlayer.mediaItemCount == 4 && freshPlayer.currentMediaItemIndex == 2 }

        assertEquals(listOf("A", "B", "C", "D"), (0 until freshPlayer.mediaItemCount).map {
            freshPlayer.getMediaItemAt(it).mediaId
        })
        assertEquals(2, freshPlayer.currentMediaItemIndex)
        assertEquals(40_000L, freshPlayer.currentPosition)
        assertEquals(Player.REPEAT_MODE_ONE, freshPlayer.repeatMode)
    }
}
