/*
 * PlaylistTimeline.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.Util
import com.google.common.collect.ImmutableList

/**
 * This class wraps a simple playlist provided as List<MediaItem>
 * to be usable as a Media3 Timeline.
 */
@SuppressLint("UnsafeOptInUsageError")
class PlaylistTimeline @JvmOverloads constructor(
    mediaItems: List<MediaItem>,
    shuffledIndices: IntArray = createUnshuffledIndices(
        mediaItems.size
    )
) : Timeline() {
    private val mediaItems: ImmutableList<MediaItem>
    private val shuffledIndices: IntArray
    private val indicesInShuffled: IntArray
    override fun getWindowCount(): Int = mediaItems.size

    override fun getWindow(
        windowIndex: Int,
        window: Window,
        defaultPositionProjectionUs: Long
    ): Window {
        window[
            0, mediaItems[windowIndex], null, 0, 0, 0, true, false, null, 0, Util.msToUs(
                DEFAULT_DURATION_MS
            ), windowIndex, windowIndex
        ] =
            0
        window.isPlaceholder = false
        return window
    }

    override fun getNextWindowIndex(
        windowIndex: Int,
        repeatMode: @Player.RepeatMode Int,
        shuffleModeEnabled: Boolean
    ): Int {
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            return windowIndex
        }
        if (windowIndex == getLastWindowIndex(shuffleModeEnabled)) {
            return if (repeatMode == Player.REPEAT_MODE_ALL) {
                getFirstWindowIndex(shuffleModeEnabled)
            } else {
                C.INDEX_UNSET
            }
        }
        return if (shuffleModeEnabled) {
            shuffledIndices[indicesInShuffled[windowIndex] + 1]
        } else {
            windowIndex + 1
        }
    }

    override fun getPreviousWindowIndex(
        windowIndex: Int,
        repeatMode: @Player.RepeatMode Int,
        shuffleModeEnabled: Boolean
    ): Int {
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            return windowIndex
        }
        if (windowIndex == getFirstWindowIndex(shuffleModeEnabled)) {
            return if (repeatMode == Player.REPEAT_MODE_ALL) {
                getLastWindowIndex(shuffleModeEnabled)
            } else {
                C.INDEX_UNSET
            }
        }
        return if (shuffleModeEnabled) {
            shuffledIndices[indicesInShuffled[windowIndex] - 1]
        } else {
            windowIndex - 1
        }
    }

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int {
        if (isEmpty) {
            return C.INDEX_UNSET
        }
        return if (shuffleModeEnabled) shuffledIndices[windowCount - 1] else windowCount - 1
    }

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int {
        if (isEmpty) {
            return C.INDEX_UNSET
        }
        return if (shuffleModeEnabled) shuffledIndices[0] else 0
    }

    override fun getPeriodCount(): Int = windowCount

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        period[null, null, periodIndex, Util.msToUs(DEFAULT_DURATION_MS)] =
            0
        return period
    }

    override fun getIndexOfPeriod(uid: Any): Int = throw UnsupportedOperationException()

    override fun getUidOfPeriod(periodIndex: Int): Any = throw UnsupportedOperationException()

    companion object {
        private const val DEFAULT_DURATION_MS: Long = 100
        private fun createUnshuffledIndices(length: Int): IntArray {
            val indices = IntArray(length)
            for (i in 0 until length) {
                indices[i] = i
            }
            return indices
        }
    }

    init {
        Assertions.checkState(mediaItems.size == shuffledIndices.size)
        this.mediaItems = ImmutableList.copyOf(mediaItems)
        this.shuffledIndices = shuffledIndices.copyOf(shuffledIndices.size)
        indicesInShuffled = IntArray(shuffledIndices.size)
        for (i in shuffledIndices.indices) {
            indicesInShuffled[shuffledIndices[i]] = i
        }
    }
}
