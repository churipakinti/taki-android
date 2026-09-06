/*
 * PlaybackUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import androidx.compose.runtime.Immutable

/**
 * The small, Compose-friendly projection of "what is playing right now" that
 * [PlaybackUiStateHolder] exposes. Deliberately minimal for issue #9 - just what the
 * near-term leaf surfaces (mini-player continuity, a now-playing chip) need. It grows one
 * field at a time as screens are migrated (issue #10), never speculatively.
 *
 * Contains no `androidx.media3` types: playback phase is [PlaybackPhase], not a Media3 int.
 */
@Immutable
data class PlayerUiState(
    val hasCurrentTrack: Boolean = false,
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    /** Opaque cover-art reference (server cover-art id). Resolved to an image at the call site. */
    val coverArtId: String? = null,
    val isPlaying: Boolean = false,
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val isCurrentTrackLiked: Boolean = false,
    val durationMs: Long = 0L,
)

/**
 * Media3 playback state, mapped to a Taki enum so no `androidx.media3` constant leaks into
 * the `ui` layer.
 */
enum class PlaybackPhase {
    Idle,
    Buffering,
    Ready,
    Ended,
    ;

    companion object {
        // Values of androidx.media3.common.Player.STATE_IDLE / _BUFFERING / _READY / _ENDED.
        private const val MEDIA3_STATE_IDLE = 1
        private const val MEDIA3_STATE_BUFFERING = 2
        private const val MEDIA3_STATE_READY = 3
        private const val MEDIA3_STATE_ENDED = 4

        fun fromMedia3State(state: Int): PlaybackPhase = when (state) {
            MEDIA3_STATE_BUFFERING -> Buffering
            MEDIA3_STATE_READY -> Ready
            MEDIA3_STATE_ENDED -> Ended
            MEDIA3_STATE_IDLE -> Idle
            else -> Idle
        }
    }
}

/**
 * A point-in-time read of the transport position. Not part of [PlayerUiState] because it
 * changes every frame; a consumer polls [PlaybackUiStateHolder.snapshotProgress] on its own
 * ticker and derives the visible progress with `derivedStateOf`.
 */
@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)
