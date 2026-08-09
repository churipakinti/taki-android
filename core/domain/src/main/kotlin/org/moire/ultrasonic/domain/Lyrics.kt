package org.moire.ultrasonic.domain

/**
 * Song lyrics. [lines] is populated whenever the source could be split into individual lines
 * (both synced and plain unsynced lyrics); [synced] is true only when every line carries a
 * real timestamp in [LyricsLine.start], i.e. karaoke-style highlighting is possible.
 */
data class Lyrics(
    val artist: String? = null,
    val title: String? = null,
    val text: String? = null,
    val synced: Boolean = false,
    val lines: List<LyricsLine> = emptyList()
)

/**
 * A single line of lyrics. [start] is the playback position in milliseconds at which this
 * line becomes active, or null when the source has no timing information for it.
 */
data class LyricsLine(val start: Long?, val value: String)
