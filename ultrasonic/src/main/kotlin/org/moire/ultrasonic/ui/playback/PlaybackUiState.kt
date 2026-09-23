/*
 * PlaybackUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import androidx.compose.runtime.Immutable
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The small, Compose-friendly projection of "what is playing right now" that
 * [PlaybackUiStateHolder] exposes. Started minimal for issue #9 (mini-player continuity) and
 * grows one field at a time as screens are migrated (issue #10); phase 4J (Now Playing) added
 * the fields below [artworkModel].
 *
 * Contains no `androidx.media3` types: playback phase is [PlaybackPhase], not a Media3 int, and
 * [repeatMode] is [RepeatMode], not a Media3 `Player.REPEAT_MODE_*` int.
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
    /** Coil model for the current track's small cover (issue #10 phase 4I: the mini-player),
     *  or null for the neutral placeholder. Resolved by the holder's artwork seam. */
    val artworkModel: CoverArtRequest? = null,
    /** The same cover at Now Playing's hero size (issue #10 phase 4J) - a distinct cache key
     *  from [artworkModel] (`FileUtil.getAlbumArtKey(..., large = true)`), so it is its own
     *  field rather than a size parameter on the same request. */
    val artworkModelLarge: CoverArtRequest? = null,
    /** For the Now Playing title tap ("go to album") - `Track.albumId` (id3) or null (folder
     *  mode uses [parentId] instead, exactly like the legacy `menu_show_album` handler). */
    val albumId: String? = null,
    /** For the Now Playing artist tap/menu ("go to artist") - `Track.artistId`, or null when the
     *  server gave no id3 artist. */
    val artistId: String? = null,
    /** `Track.parent` - the folder-mode fallback album id, and what the queue row's context
     *  menu uses to decide whether "Go to Album" applies at all. */
    val parentId: String? = null,
    /** The current track's position in [org.moire.ultrasonic.service.MediaPlayerManager
     *  .playlistInPlayOrder] (shuffle-aware) - `RxBus.StateWithTrack.index`, carried through
     *  unchanged. Marks the "now playing" row in the queue. */
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Whether the transport buttons are enabled - a point read of
     *  `MediaPlayerManager.canSeekToPrevious/Next()`, refreshed on every playback event exactly
     *  like the legacy `updateMediaButtonActivationState()`. */
    val canSeekToPrevious: Boolean = false,
    val canSeekToNext: Boolean = false,
    /** Whether Jukebox (remote playback through the server) is on - not togglable from Now
     *  Playing (the legacy menu item was already always hidden/disabled; see the phase 4J
     *  report), but it still changes whether the seek bar is draggable while paused. */
    val isJukeboxEnabled: Boolean = false,
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
 * Media3 repeat mode, mapped to a Taki enum (issue #10 phase 4J) so no `androidx.media3`/
 * `Player.REPEAT_MODE_*` int leaks into the `ui` layer. The int values happen to line up
 * ([fromInt]/[toInt]) since `MediaPlayerManager.repeatMode` is itself a passthrough to
 * `Player.repeatMode`, but call sites should never rely on that - always go through here.
 */
enum class RepeatMode {
    OFF,
    ONE,
    ALL,
    ;

    companion object {
        private const val MEDIA3_REPEAT_OFF = 0
        private const val MEDIA3_REPEAT_ONE = 1
        private const val MEDIA3_REPEAT_ALL = 2

        fun fromInt(mode: Int): RepeatMode = when (mode) {
            MEDIA3_REPEAT_ONE -> ONE
            MEDIA3_REPEAT_ALL -> ALL
            else -> OFF
        }
    }

    fun toInt(): Int = when (this) {
        OFF -> MEDIA3_REPEAT_OFF
        ONE -> MEDIA3_REPEAT_ONE
        ALL -> MEDIA3_REPEAT_ALL
    }

    /** The next mode in the legacy cycle (Off -> Song -> All -> Off), from the repeat button. */
    fun next(): RepeatMode = when (this) {
        OFF -> ONE
        ONE -> ALL
        ALL -> OFF
    }
}

/**
 * A point-in-time read of transport position/duration/buffering. Not part of [PlayerUiState]
 * because it changes continuously; a consumer polls [PlaybackUiStateHolder.snapshotProgress] on
 * its own ticker and derives the visible progress with `derivedStateOf`.
 */
@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** `MediaPlayerManager.bufferedPercentage` (0-100), for the Now Playing seek bar's
     *  secondary/buffered indicator (issue #10 phase 4J). Always 0 for the mini-player, which
     *  never reads it. */
    val bufferedPercent: Int = 0,
)
