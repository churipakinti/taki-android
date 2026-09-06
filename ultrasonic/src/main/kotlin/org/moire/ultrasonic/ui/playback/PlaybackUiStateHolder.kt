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
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.RxBus

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
 *   Compose  ->  onPlayPause() / onNext() / onPrevious()  ->  MediaPlayerManager
 *
 * This holder is **not a source of truth**. It never stores a playback decision of its own,
 * never mutates state optimistically, never holds a `MediaController` / `Player` / session,
 * and exposes no `androidx.media3` type. Every field it emits is derived from an upstream
 * RxBus emission; every command is forwarded to [MediaPlayerManager] unchanged, and the new
 * value comes back through the read path.
 *
 * Scope for issue #9 is intentionally tiny (see [PlayerUiState]); it grows per migrated
 * screen in issue #10 (queue, seek, shuffle/repeat, per-row download state, ...).
 *
 * See docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md section 2.3.
 */
class PlaybackUiStateHolder(
    private val mediaPlayerManager: MediaPlayerManager,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
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
     * A point-in-time read of transport position/duration, for a consumer-owned ticker.
     * Never emitted as state (it changes every frame).
     */
    fun snapshotProgress(): PlaybackProgress = PlaybackProgress(
        positionMs = mediaPlayerManager.playerPosition.toLong().coerceAtLeast(0L),
        durationMs = mediaPlayerManager.playerDuration.toLong().coerceAtLeast(0L),
    )

    /** Toggle play/pause. Forwarded verbatim; the resulting state returns via [playerState]. */
    fun onPlayPause() = mediaPlayerManager.togglePlayPause()

    /** Skip to next. Forwarded verbatim. */
    fun onNext() = mediaPlayerManager.seekToNext()

    /** Skip to previous. Forwarded verbatim. */
    fun onPrevious() = mediaPlayerManager.seekToPrevious()
}

/**
 * Bridge a hot RxJava [Observable] to a cold [kotlinx.coroutines.flow.Flow]. A `replay(1)`
 * source (as the RxBus player/playlist observables are) replays its last value on subscribe.
 */
private fun <T : Any> Observable<T>.asFlow() = callbackFlow {
    val disposable = subscribe(
        { value -> trySend(value) },
        { error -> close(error) },
    )
    awaitClose { disposable.dispose() }
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
    )
}
