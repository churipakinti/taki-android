/*
 * UpNextUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.upnext

import androidx.compose.runtime.Immutable

/** The long-press actions a queued row offers (the legacy `nowplaying_context` menu). */
enum class UpNextMenuItem { GO_TO_ARTIST, GO_TO_ALBUM, LYRICS, SHUFFLE, FAVORITE }

/** One item of the shuffle-aware play order, reduced to what the Up Next projection needs. The
 *  host maps `MediaPlayerManager.playlistInPlayOrder` to these - the screen never sees a Media3
 *  type. */
@Immutable
data class QueueEntry(
    val mediaId: String,
    val title: String,
    val artist: String?,
    val menuItems: List<UpNextMenuItem> = emptyList(),
)

/** One upcoming row. [playOrderIndex] is the item's position in the shuffle-aware play order -
 *  the same index space `MediaPlayerManager.getUnshuffledIndexOf` (tap-to-play, delete) and the
 *  legacy queue's reorder use. [key] is unique within the queue even for duplicate tracks. */
@Immutable
data class UpNextTrackUi(
    val key: String,
    val title: String,
    val artist: String?,
    val playOrderIndex: Int,
    val menuItems: List<UpNextMenuItem>,
)

/** The currently playing track as shown in the "Now playing" section. */
@Immutable
data class UpNextCurrentUi(
    val title: String,
    val artist: String?,
    val artworkModel: Any?,
    val isPlaying: Boolean,
)

/** Everything the Up Next screen renders: a read-only projection of the runtime queue. */
@Immutable
data class UpNextUiState(
    val current: UpNextCurrentUi? = null,
    val upcoming: List<UpNextTrackUi> = emptyList(),
)

/**
 * The tracks after the current one, in play order (issue #10 phase 4K1). [entries] must be the
 * shuffle-aware order (`playlistInPlayOrder`), never the raw playlist; [currentIndex] is
 * `PlayerUiState.currentIndex` (a position in that same order, -1 when nothing is current, in
 * which case everything is upcoming). Keys count prior occurrences over the *whole* order, so a
 * duplicate track keeps the same key when earlier items are played past.
 */
fun buildUpcoming(entries: List<QueueEntry>, currentIndex: Int): List<UpNextTrackUi> {
    val seen = HashMap<String, Int>()
    val keyed = entries.map { entry ->
        val occurrence = seen.merge(entry.mediaId, 1, Int::plus)!!
        "${entry.mediaId}#$occurrence"
    }
    val start = (currentIndex + 1).coerceAtLeast(0)
    return (start until entries.size).map { index ->
        val entry = entries[index]
        UpNextTrackUi(
            key = keyed[index],
            title = entry.title,
            artist = entry.artist,
            playOrderIndex = index,
            menuItems = entry.menuItems,
        )
    }
}

/** [list] with the item at [from] moved to [to] (both indices into [list]); a no-op for an
 *  out-of-range or unchanged move. */
fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
    if (from == to || from !in list.indices || to !in list.indices) return list
    val mutable = list.toMutableList()
    mutable.add(to, mutable.removeAt(from))
    return mutable
}

/** The play-order position of the upcoming row at [upcomingIndex] - the number the legacy queue
 *  passed to `moveItemInPlaylist` / `getUnshuffledIndexOf`. */
fun playOrderPosition(currentIndex: Int, upcomingIndex: Int): Int =
    (currentIndex + 1).coerceAtLeast(0) + upcomingIndex

/** Commands the Up Next screen fires; the host `PlayerFragment` maps each onto the unchanged
 *  runtime (`MediaPlayerManager.play` / `moveItemInPlaylist` / `removeFromPlaylist`). */
data class UpNextActions(
    val onBack: () -> Unit,
    /** Tap-to-play: the row's play-order position. */
    val onPlay: (playOrderPosition: Int) -> Unit,
    /** A finished drag: both arguments are play-order positions (the legacy queue's contract). */
    val onMove: (fromPlayOrderPosition: Int, toPlayOrderPosition: Int) -> Unit,
    val onRemove: (playOrderPosition: Int) -> Unit,
    val onMenuItem: (playOrderPosition: Int, item: UpNextMenuItem) -> Unit,
) {
    companion object {
        val Noop = UpNextActions({}, {}, { _, _ -> }, {}, { _, _ -> })
    }
}
