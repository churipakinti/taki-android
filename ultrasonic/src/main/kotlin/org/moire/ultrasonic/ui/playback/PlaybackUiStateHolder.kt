/*
 * PlaybackUiStateHolder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playback

import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.SleepTimerState

private const val MILLIS_PER_SECOND = 1000L
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The one-way, additive bridge between playback/runtime state and Compose.
 *
 * Data flows exactly one direction:
 *
 *   MediaPlayerManager / services / RxBus  ->  PlaybackUiStateHolder  ->  Compose
 *
 * Commands flow the other way and bypass this class entirely:
 *
 *   Compose  ->  onPlayPause() / onNext() / onPrevious() / ...  ->  MediaPlayerManager
 *
 * This holder is **not a source of truth**. It never stores a playback decision of its own,
 * never mutates state optimistically, never holds a `MediaController` / `Player` / session,
 * and exposes no `androidx.media3` type. Every field it emits is derived from an upstream
 * RxBus emission or a direct point-in-time `MediaPlayerManager` read; every command is
 * forwarded to [MediaPlayerManager] unchanged, and the new value comes back through the read
 * path - there is still exactly one authoritative projection, shared by the mini-player
 * (issue #10 phase 4I) and Now Playing (phase 4J).
 *
 * [SleepTimerState] is reused verbatim rather than re-wrapped: it already carries no
 * `androidx.media3`/Android-UI type (only `SystemClock`, a plain value holder), so passing it
 * through directly avoids a parallel duplicate type.
 *
 * See docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md section 2.3.
 */
class PlaybackUiStateHolder(
    private val mediaPlayerManager: MediaPlayerManager,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    /** Builds the Coil model for a track's small (mini-player) cover. Pure and cheap (an md5 of
     *  the album path); a seam because the default touches `Storage`, which JVM tests do not
     *  have. */
    private val artworkResolver: (Track) -> CoverArtRequest? = { it.coverArtRequestOrNull() },
    /** The same, at Now Playing's hero size (issue #10 phase 4J) - a distinct cache key
     *  (`large = true`). */
    private val largeArtworkResolver: (Track) -> CoverArtRequest? =
        { it.coverArtRequestOrNull(large = true) },
) {

    /**
     * The current-track projection. Backed by [RxBus.playerStateObservable] (which already
     * `replay(1)`s), mapped to a Media3-free [PlayerUiState]. The non-throttled observable is
     * used deliberately: it emits synchronously on the publishing thread, which keeps this
     * holder and its tests simple. A consumer that needs rate limiting (a list, a fast
     * progress readout) debounces on its own.
     */
    val playerState: StateFlow<PlayerUiState> =
        RxBus.playerStateObservable
            .asFlow()
            .map { it.toUiState() }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = PlayerUiState(),
            )

    /**
     * The sleep timer projection (issue #10 phase 4J), for the Now Playing sleep-timer button's
     * active/inactive tint and content description. A separate `StateFlow` from [playerState]
     * on purpose: it changes on its own, independent cadence (armed/expired/cancelled), and
     * folding it into every playback event would recompose Now Playing's whole transport row
     * for an unrelated change.
     */
    val sleepTimerState: StateFlow<SleepTimerState> =
        RxBus.sleepTimerStateObservable
            .asFlow()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SleepTimerState.Off,
            )

    /**
     * A point-in-time read of transport position/duration/buffering, for a consumer-owned
     * ticker. Never emitted as state (it changes continuously).
     */
    fun snapshotProgress(): PlaybackProgress = PlaybackProgress(
        positionMs = mediaPlayerManager.playerPosition.toLong().coerceAtLeast(0L),
        durationMs = mediaPlayerManager.playerDuration.toLong().coerceAtLeast(0L),
        bufferedPercent = mediaPlayerManager.bufferedPercentage,
    )

    /** Toggle play/pause. Forwarded verbatim; the resulting state returns via [playerState]. */
    fun onPlayPause() = mediaPlayerManager.togglePlayPause()

    /** Skip to next. Forwarded verbatim. */
    fun onNext() = mediaPlayerManager.seekToNext()

    /** Skip to previous. Forwarded verbatim. */
    fun onPrevious() = mediaPlayerManager.seekToPrevious()

    /** Now Playing only (issue #10 phase 4J): cancel playback entirely - the legacy "Stop"
     *  button shown in place of Play/Pause while buffering. */
    fun onStop() = mediaPlayerManager.reset()

    /** Seek to an absolute position, from the Now Playing seek bar being dropped. */
    fun onSeekTo(positionMs: Int) = mediaPlayerManager.seekTo(positionMs)

    /** The legacy previous/next buttons' long-press-and-hold repeat action: rewind/fast-forward
     *  within the current track, rather than skip. */
    fun onSeekBack() = mediaPlayerManager.seekBack()
    fun onSeekForward() = mediaPlayerManager.seekForward()

    /** Returns the new state, exactly like `MediaPlayerManager.toggleShuffle()` - the caller
     *  (Now Playing) uses it for the confirmation toast, as the legacy screen did. */
    fun onToggleShuffle(): Boolean = mediaPlayerManager.toggleShuffle()

    /** Advances to the next mode in the legacy Off -> Song -> All -> Off cycle and returns it,
     *  for the caller's confirmation toast. */
    fun onCycleRepeat(): RepeatMode {
        val next = RepeatMode.fromInt(mediaPlayerManager.repeatMode).next()
        mediaPlayerManager.repeatMode = next.toInt()
        return next
    }

    fun onSetSleepTimer(minutes: Int) = mediaPlayerManager.setSleepTimer(minutes)
    fun onSetSleepTimerEndOfTrack() = mediaPlayerManager.setSleepTimerEndOfTrack()
    fun onCancelSleepTimer() = mediaPlayerManager.cancelSleepTimer()

    /** The overflow menu's "Clear Playlist" - unlike a manual shuffle toggle, the legacy screen
     *  always turns shuffle off first so the (now empty) queue doesn't stay marked shuffled. */
    fun onClearQueue() {
        mediaPlayerManager.isShufflePlayEnabled = false
        mediaPlayerManager.clear()
    }

    private fun RxBus.StateWithTrack.toUiState(): PlayerUiState {
        val current: Track? = track
        return PlayerUiState(
            hasCurrentTrack = current != null,
            trackId = current?.id,
            title = current?.title,
            artist = current?.artist,
            coverArtId = current?.coverArt,
            isPlaying = isPlaying,
            phase = PlaybackPhase.fromMedia3State(state),
            isCurrentTrackLiked = current?.starred == true,
            durationMs = current?.duration?.let { it.toLong() * MILLIS_PER_SECOND } ?: 0L,
            artworkModel = current?.let(artworkResolver),
            artworkModelLarge = current?.let(largeArtworkResolver),
            albumId = current?.albumId,
            artistId = current?.artistId,
            parentId = current?.parent,
            currentIndex = index,
            isShuffleEnabled = mediaPlayerManager.isShufflePlayEnabled,
            repeatMode = RepeatMode.fromInt(mediaPlayerManager.repeatMode),
            canSeekToPrevious = mediaPlayerManager.canSeekToPrevious(),
            canSeekToNext = mediaPlayerManager.canSeekToNext(),
            isJukeboxEnabled = mediaPlayerManager.isJukeboxEnabled,
        )
    }
}

/**
 * Bridge a hot RxJava [Observable] to a cold [kotlinx.coroutines.flow.Flow]. A `replay(1)`
 * source (as the RxBus player/playlist/sleep-timer observables are) replays its last value on
 * subscribe.
 */
private fun <T : Any> Observable<T>.asFlow() = callbackFlow {
    val disposable = subscribe(
        { value -> trySend(value) },
        { error -> close(error) },
    )
    awaitClose { disposable.dispose() }
}
