/*
 * PlaybackStateSerializer.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.Constants
import org.moire.ultrasonic.util.FileUtil
import timber.log.Timber

// A stuck or rapidly re-buffering player (issue #17) drove serializeAsync ~7x/second, rewriting
// the state file each time even though only the position had nudged. These bound how often a
// position-only change is allowed to reach disk; a structural change (queue / index / shuffle /
// repeat) always bypasses them.
internal const val POSITION_WRITE_MIN_INTERVAL_MS = 3_000L
internal const val POSITION_WRITE_MIN_DELTA_MS = 1_000L

/**
 * This class is responsible for the serialization / deserialization
 * of the playlist and the player state (e.g. current playing number and play position)
 * to the filesystem.
 *
 * TODO: Should use: MediaItemsWithStartPosition
 */
class PlaybackStateSerializer : KoinComponent {

    private val context by inject<Context>()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var lastSerializedTracks: List<Track> = emptyList()

    private val writeGate = PlaybackStateWriteGate()

    /**
     * Fire-and-forget persist. Structural changes (queue / index / shuffle / repeat) are written
     * straight away; position-only churn is rate-limited by [writeGate] so a stalled or rapidly
     * flapping player can't rewrite the state file several times a second (issue #17). The
     * synchronous shutdown flush goes through [serializeNow] directly and is never gated.
     */
    fun serializeAsync(
        songs: Iterable<Track>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int,
        shufflePlay: Boolean,
        repeatMode: Int
    ) {
        if (isSerializing.get() || !isSetup.get()) return

        val songList = songs.toList()
        val signature = structuralSignature(songList, currentPlayingIndex, shufflePlay, repeatMode)
        if (!writeGate.shouldWrite(
                signature,
                currentPlayingPosition,
                SystemClock.elapsedRealtime()
            )
        ) {
            return
        }

        isSerializing.set(true)

        ioScope.launch {
            serializeNow(
                songList,
                currentPlayingIndex,
                currentPlayingPosition,
                shufflePlay,
                repeatMode
            )
        }.invokeOnCompletion {
            isSerializing.set(false)
        }
    }

    /**
     * Identity of everything except the play position: two calls with the same signature differ
     * only in how far the current track has advanced.
     */
    private fun structuralSignature(
        tracks: List<Track>,
        currentPlayingIndex: Int,
        shufflePlay: Boolean,
        repeatMode: Int
    ): String {
        val builder = StringBuilder(tracks.size * 8 + 24)
        builder.append(currentPlayingIndex).append('|')
            .append(shufflePlay).append('|')
            .append(repeatMode).append('|')
            .append(tracks.size).append('|')
        for (track in tracks) builder.append(track.id).append(',')
        return builder.toString()
    }

    fun serializeCheckpointAsync(
        currentPlayingIndex: Int,
        currentPlayingPosition: Int,
        shufflePlay: Boolean,
        repeatMode: Int
    ) {
        val tracks = lastSerializedTracks
        if (tracks.isEmpty()) return
        serializeAsync(
            tracks,
            currentPlayingIndex,
            currentPlayingPosition,
            shufflePlay,
            repeatMode
        )
    }

    val isReady: Boolean get() = isSetup.get()

    @Synchronized
    internal fun serializeNow(
        tracks: Iterable<Track>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int,
        shufflePlay: Boolean,
        repeatMode: Int
    ) {
        val normalizedQueue = normalizeQueue(tracks.toList(), currentPlayingIndex)
        val trackSnapshot = normalizedQueue.tracks
        lastSerializedTracks = trackSnapshot
        val state = PlaybackState(
            trackSnapshot,
            normalizedQueue.currentIndex,
            currentPlayingPosition,
            shufflePlay,
            repeatMode
        )

        Timber.i(
            "Serialized currentPlayingIndex: %d, currentPlayingPosition: %d, shuffle: %b, repeat: %d",
            state.currentPlayingIndex,
            state.currentPlayingPosition,
            state.shufflePlay,
            state.repeatMode
        )

        FileUtil.serialize(context, state, Constants.FILENAME_PLAYLIST_SER)
    }

    fun deserialize(afterDeserialized: (PlaybackState?) -> Unit?) {
        if (isDeserializing.get()) return
        // Fresh session: forget any debounce bookkeeping so the first checkpoint after restore
        // persists promptly instead of being rate-limited against the previous run's state.
        writeGate.reset()
        ioScope.launch {
            try {
                val state = deserializeNow()
                mainScope.launch {
                    afterDeserialized(state)
                }
                isSetup.set(true)
            } catch (all: Exception) {
                Timber.e(all, "Had a problem deserializing:")
            } finally {
                isDeserializing.set(false)
            }
        }
    }

    fun deserializeNow(): PlaybackState? {
        val state = FileUtil.deserialize<PlaybackState>(
            context,
            Constants.FILENAME_PLAYLIST_SER
        ) ?: return null

        val normalizedQueue = normalizeQueue(state.songs, state.currentPlayingIndex)
        val restoredState = state.copy(
            songs = normalizedQueue.tracks,
            currentPlayingIndex = normalizedQueue.currentIndex
        )
        lastSerializedTracks = restoredState.songs
        Timber.i(
            "Deserialized currentPlayingIndex: %d, currentPlayingPosition: %d, shuffle: %b, repeat: %d",
            restoredState.currentPlayingIndex,
            restoredState.currentPlayingPosition,
            restoredState.shufflePlay,
            restoredState.repeatMode
        )

        return restoredState
    }

    private fun normalizeQueue(tracks: List<Track>, currentIndex: Int): NormalizedQueue {
        if (tracks.size <= MAX_QUEUE_SIZE) return NormalizedQueue(tracks, currentIndex)

        val safeIndex = currentIndex.coerceIn(0, tracks.lastIndex)
        val start = (safeIndex - MAX_QUEUE_SIZE / 2)
            .coerceIn(0, tracks.size - MAX_QUEUE_SIZE)
        Timber.w(
            "Limiting persisted playback queue from %d to %d tracks",
            tracks.size,
            MAX_QUEUE_SIZE
        )
        return NormalizedQueue(
            tracks = tracks.subList(start, start + MAX_QUEUE_SIZE).toList(),
            currentIndex = safeIndex - start
        )
    }

    private data class NormalizedQueue(val tracks: List<Track>, val currentIndex: Int)

    companion object {
        private val isSetup = AtomicBoolean(false)
        private val isSerializing = AtomicBoolean(false)
        private val isDeserializing = AtomicBoolean(false)
    }
}

/**
 * Decides whether a [PlaybackStateSerializer.serializeAsync] request actually reaches disk.
 * Pure and self-contained so it can be unit tested with a fake clock.
 *
 * - A changed structural signature always writes (queue / index / shuffle / repeat edits must
 *   persist immediately).
 * - Otherwise the request is position-only and is dropped unless BOTH at least
 *   [minIntervalMs] has passed since the last write AND the position moved at least
 *   [minDeltaMs]. This is what stops the several-writes-per-second storm during a stall, and
 *   also trims routine position churn during normal playback.
 */
internal class PlaybackStateWriteGate(
    private val minIntervalMs: Long = POSITION_WRITE_MIN_INTERVAL_MS,
    private val minDeltaMs: Long = POSITION_WRITE_MIN_DELTA_MS
) {
    private var lastSignature: String? = null
    private var lastWriteAtMs = 0L
    private var lastPositionMs = 0

    @Synchronized
    fun shouldWrite(signature: String, positionMs: Int, nowMs: Long): Boolean {
        val structuralChange = signature != lastSignature
        val allow = structuralChange ||
            (
                nowMs - lastWriteAtMs >= minIntervalMs &&
                    abs(positionMs - lastPositionMs) >= minDeltaMs
                )

        if (allow) {
            lastSignature = signature
            lastWriteAtMs = nowMs
            lastPositionMs = positionMs
        }
        return allow
    }

    @Synchronized
    fun reset() {
        lastSignature = null
        lastWriteAtMs = 0L
        lastPositionMs = 0
    }
}
