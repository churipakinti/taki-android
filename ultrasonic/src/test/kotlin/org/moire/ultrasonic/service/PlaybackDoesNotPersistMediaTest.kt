/*
 * PlaybackDoesNotPersistMediaTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Environment
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.reactivex.rxjava3.disposables.Disposable
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.MediaItemConverter
import org.moire.ultrasonic.util.toMediaItem
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * P0-3 -- ordinary playback must never persist media.
 *
 * Taki's core contract: **Play** streams with normal Media3 buffering only; only an explicit
 * **Download** produces persistent local media, and **Offline** shows only what was explicitly
 * downloaded. A previous regression broke this: `PlaybackService.cacheNextSongs()` fed the next
 * queued tracks into the *same* persistent-download pipeline as the Download button on every
 * track/timeline/shuffle event (commit that removed it: "Stop playback from pre-downloading
 * tracks"). `cacheNextSongs()` / `Settings.PRELOAD_COUNT` no longer exist, which is a
 * compile-time guarantee on its own; this test additionally nails down the *observable* contract
 * so an equivalent reintroduction anywhere on the [MediaPlayerManager] playback path fails.
 *
 * It exercises the representative playback paths (start a track, automatic transition, manual
 * next / previous, repeat, shuffle) plus every callback on [MediaPlayerManager]'s Media3
 * [Player.Listener] -- the natural home for such a reintroduction -- and asserts that none of
 * them touch [DownloadService]'s persistent state or the filesystem.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlaybackDoesNotPersistMediaTest {

    private lateinit var manager: MediaPlayerManager
    private lateinit var player: FakeMedia3Player
    private lateinit var listener: Player.Listener
    private lateinit var downloadStateSubscription: Disposable

    private val downloadStateEmissions = mutableListOf<RxBus.TrackDownloadState>()

    private val tracks = (1..4).map { n ->
        Track(
            id = "track-$n",
            title = "Song $n",
            artist = "Artist",
            album = "Album",
            suffix = "mp3",
            path = "Artist/Album/0$n-Song $n.mp3",
            isDirectory = false
        )
    }

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        FileUtil.cachedUltrasonicDirectory = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/org.moire.ultrasonic.test"
        )
        FileUtil.ultrasonicDirectory.mkdirs()

        drainDownloadServiceState()
        downloadStateSubscription = RxBus.trackDownloadStatePublisher.subscribe {
            downloadStateEmissions.add(it)
        }

        manager = MediaPlayerManager(mock(), mock())
        player = FakeMedia3Player(Looper.getMainLooper())
        manager.setPrivateField("controller", player)
        listener = manager.getPrivateField("listeners") as Player.Listener
        player.addListener(listener)
    }

    @After
    fun tearDown() {
        downloadStateSubscription.dispose()
        drainDownloadServiceState()
        FileUtil.cachedUltrasonicDirectory?.deleteRecursively()
        MediaItemConverter.mediaItemCache.clear()
        MediaItemConverter.trackCache.clear()
    }

    private fun mediaItems(): List<MediaItem> = tracks.map { it.toMediaItem() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun settle(timeoutMs: Long = 2_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idle()
            Thread.sleep(10)
        }
        idle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun downloadQueue() =
        DownloadService::class.java.getDeclaredField("downloadQueue").apply { isAccessible = true }
            .get(null) as java.util.concurrent.PriorityBlockingQueue<DownloadableTrack>

    @Suppress("UNCHECKED_CAST")
    private fun activeDownloads() =
        DownloadService::class.java.getDeclaredField("activeDownloads").apply { isAccessible = true }
            .get(null) as java.util.concurrent.ConcurrentHashMap<String, *>

    private fun drainDownloadServiceState() {
        downloadQueue().clear()
        activeDownloads().clear()
        DownloadService.observableDownloads.postValue(emptyList())
        downloadStateEmissions.clear()
    }

    private fun assertNoPersistentDownloadHappened() {
        assertTrue("download queue must stay empty", downloadQueue().isEmpty())
        assertTrue("no active downloads", activeDownloads().isEmpty())

        tracks.forEach { track ->
            assertEquals(
                "playback must not register ${track.id} as downloaded/queued",
                DownloadState.IDLE,
                DownloadService.getDownloadState(track)
            )
        }

        val observable = DownloadService.observableDownloads.value
        assertTrue("nothing published as downloading", observable == null || observable.isEmpty())

        assertTrue(
            "playback must not post any download state, saw: $downloadStateEmissions",
            downloadStateEmissions.isEmpty()
        )

        val completeFiles = FileUtil.cachedUltrasonicDirectory
            ?.walkTopDown()
            ?.filter { it.isFile && (it.name.contains(".complete") || it.name.contains(".partial")) }
            ?.toList()
            .orEmpty()
        assertTrue("playback must not write .complete/.partial media, found: $completeFiles", completeFiles.isEmpty())
    }

    @Test
    fun `starting a selected track does not pre-download anything`() {
        player.setMediaItems(mediaItems())
        manager.play(0)
        settle()

        assertNoPersistentDownloadHappened()
    }

    @Test
    fun `sequential automatic transition does not pre-download the next track`() {
        player.setMediaItems(mediaItems())
        manager.play(0)
        idle()

        // Force a natural progression through the manager's real Media3 listener.
        listener.onMediaItemTransition(
            player.getMediaItemAt(1),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )
        listener.onMediaItemTransition(
            player.getMediaItemAt(2),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )
        settle()

        assertNoPersistentDownloadHappened()
    }

    @Test
    fun `manual next and previous do not pre-download`() {
        player.setMediaItems(mediaItems())
        manager.play(0)
        idle()

        manager.seekToNext()
        idle()
        manager.seekToNext()
        idle()
        manager.seekToPrevious()
        settle()

        assertNoPersistentDownloadHappened()
    }

    @Test
    fun `repeat-one looping does not pre-download`() {
        player.setMediaItems(mediaItems())
        manager.play(0)
        manager.repeatMode = Player.REPEAT_MODE_ONE
        idle()

        listener.onMediaItemTransition(
            player.getMediaItemAt(0),
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        )
        settle()

        assertNoPersistentDownloadHappened()
    }

    @Test
    fun `enabling shuffle does not pre-download the reshuffled queue`() {
        player.setMediaItems(mediaItems())
        manager.play(0)
        idle()

        manager.toggleShuffle()
        settle()

        assertTrue(player.shuffleModeEnabled)
        assertNoPersistentDownloadHappened()
    }

    @Test
    fun `firing every media3 listener callback does not pre-download`() {
        player.setMediaItems(mediaItems())
        manager.play(0)
        idle()

        val timeline = player.currentTimeline
        listener.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        listener.onPlaybackStateChanged(Player.STATE_READY)
        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        listener.onIsPlayingChanged(true)
        listener.onIsPlayingChanged(false)
        listener.onShuffleModeEnabledChanged(true)
        listener.onRepeatModeChanged(Player.REPEAT_MODE_ALL)
        listener.onMediaItemTransition(
            player.getMediaItemAt(1),
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        )
        settle()

        assertNoPersistentDownloadHappened()
    }
}
