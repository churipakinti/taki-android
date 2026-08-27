/*
 * FakeMedia3Player.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A minimal in-memory [Player] backed by [SimpleBasePlayer], used by the P0 runtime regression
 * tests that need a player with a *real* [androidx.media3.common.Timeline] (so
 * [MediaPlayerManager.seekTo]'s empty-timeline / out-of-range guards behave as in production) but
 * no actual decoding, service binding or playback thread.
 *
 * Only the operations the tests drive are implemented: set/add/remove items, seek, prepare,
 * play/pause (via play-when-ready), shuffle and repeat. Everything else keeps [SimpleBasePlayer]'s
 * defaults. It must be created and driven on [applicationLooper]'s thread, matching how the real
 * `MediaController` is confined to the app main thread.
 */
@UnstableApi
internal class FakeMedia3Player(
    applicationLooper: Looper = Looper.getMainLooper()
) : SimpleBasePlayer(applicationLooper) {

    private val items = mutableListOf<MediaItem>()
    private var currentIndex = 0
    private var positionMs = 0L
    private var shuffle = false
    private var repeat = Player.REPEAT_MODE_OFF
    private var prepared = false
    private var playWhenReady = false

    private val allCommands = Player.Commands.Builder().addAllCommands().build()

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(allCommands)
            .setShuffleModeEnabled(shuffle)
            .setRepeatMode(repeat)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        if (items.isEmpty()) {
            builder.setPlaybackState(Player.STATE_IDLE)
        } else {
            builder.setPlaylist(items.map(::mediaItemData))
            builder.setCurrentMediaItemIndex(currentIndex.coerceIn(0, items.lastIndex))
            builder.setContentPositionMs(positionMs)
            builder.setPlaybackState(if (prepared) Player.STATE_READY else Player.STATE_IDLE)
        }
        return builder.build()
    }

    private fun mediaItemData(item: MediaItem): MediaItemData =
        MediaItemData.Builder(uidOf(item))
            .setMediaItem(item)
            .setIsSeekable(true)
            .setIsDynamic(false)
            .setDurationUs(FAKE_DURATION_US)
            .build()

    private fun uidOf(item: MediaItem): Any = item.mediaId.ifEmpty { System.identityHashCode(item) }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        items.clear()
        items.addAll(mediaItems)
        currentIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        positionMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs
        prepared = false
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<*> {
        items.addAll(index, mediaItems)
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        items.subList(fromIndex, toIndex).clear()
        if (items.isEmpty()) {
            currentIndex = 0
            positionMs = 0L
        } else {
            currentIndex = currentIndex.coerceIn(0, items.lastIndex)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET) currentIndex = mediaItemIndex
        this.positionMs = if (positionMs == C.TIME_UNSET) 0L else positionMs
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        shuffle = shuffleModeEnabled
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        prepared = false
        playWhenReady = false
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        // Any positive, finite duration - the tests never assert on it, they only need the
        // windows to be seekable and non-live.
        const val FAKE_DURATION_US = 300_000_000L
    }
}
