/*
 * PlaybackStateSerializer.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
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

    fun serializeAsync(
        songs: Iterable<Track>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int,
        shufflePlay: Boolean,
        repeatMode: Int
    ) {
        if (isSerializing.get() || !isSetup.get()) return

        isSerializing.set(true)

        ioScope.launch {
            serializeNow(
                songs,
                currentPlayingIndex,
                currentPlayingPosition,
                shufflePlay,
                repeatMode
            )
        }.invokeOnCompletion {
            isSerializing.set(false)
        }
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
        if (tracks.size <= MAX_PERSISTED_QUEUE_SIZE) return NormalizedQueue(tracks, currentIndex)

        val safeIndex = currentIndex.coerceIn(0, tracks.lastIndex)
        val start = (safeIndex - MAX_PERSISTED_QUEUE_SIZE / 2)
            .coerceIn(0, tracks.size - MAX_PERSISTED_QUEUE_SIZE)
        Timber.w(
            "Limiting persisted playback queue from %d to %d tracks",
            tracks.size,
            MAX_PERSISTED_QUEUE_SIZE
        )
        return NormalizedQueue(
            tracks = tracks.subList(start, start + MAX_PERSISTED_QUEUE_SIZE).toList(),
            currentIndex = safeIndex - start
        )
    }

    private data class NormalizedQueue(val tracks: List<Track>, val currentIndex: Int)

    companion object {
        private const val MAX_PERSISTED_QUEUE_SIZE = 100
        private val isSetup = AtomicBoolean(false)
        private val isSerializing = AtomicBoolean(false)
        private val isDeserializing = AtomicBoolean(false)
    }
}
