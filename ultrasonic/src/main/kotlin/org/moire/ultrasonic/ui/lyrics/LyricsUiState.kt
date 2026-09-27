/*
 * LyricsUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.lyrics

import androidx.compose.runtime.Immutable

/**
 * Everything the Compose Lyrics screen (issue #10 phase 4K3) shows: whose lyrics these are and
 * what became of loading them. The track identity comes from the playing track, never from the
 * lyrics payload, so the header is right even while loading or when there is nothing to show.
 */
@Immutable
data class LyricsUiState(
    val trackId: String? = null,
    val title: String = "",
    val artist: String? = null,
    val content: LyricsContent = LyricsContent.Loading,
)

/** What the lyrics body is. [Synced] only when the source really carries timestamps. */
sealed interface LyricsContent {
    data object Loading : LyricsContent

    /** Loaded, and the server has nothing for this track. */
    data object Empty : LyricsContent

    /** Every source failed (network / server) - retry is meaningful. */
    data object Error : LyricsContent

    /** Plain text, one entry per source line; a blank entry is a stanza break. */
    @Immutable
    data class Plain(val lines: List<String>) : LyricsContent

    /** Time-synced lines, ascending by [LyricsLineUi.startMs]. */
    @Immutable
    data class Synced(val lines: List<LyricsLineUi>) : LyricsContent
}

@Immutable
data class LyricsLineUi(val startMs: Long, val text: String)

@Immutable
data class LyricsActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    /** Seek the current track to a synced line's timestamp. */
    val onSeek: (positionMs: Long) -> Unit,
)

/**
 * Index of the line being sung at [positionMs] - the last line starting at or before it - or -1
 * before the first line. [lines] must be ascending by start; binary search, so cheap enough for
 * the ticker.
 */
fun activeLineIndex(lines: List<LyricsLineUi>, positionMs: Long): Int {
    var low = 0
    var high = lines.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].startMs <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}
