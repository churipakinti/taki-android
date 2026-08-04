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

    val isReady: Boolean get() = isSetup.get()

    @Synchronized
    internal fun serializeNow(
        tracks: Iterable<Track>,
        currentPlayingIndex: Int,
        currentPlayingPosition: Int,
        shufflePlay: Boolean,
        repeatMode: Int
    ) {
        val state = PlaybackState(
            tracks.toList(),
            currentPlayingIndex,
            currentPlayingPosition,
            shufflePlay,
            repeatMode
        )

        Timber.i(
            "Serialized currentPlayingIndex: %d, currentPlayingPosition: %d, shuffle: %b",
            state.currentPlayingIndex,
            state.currentPlayingPosition,
            state.shufflePlay
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

        Timber.i(
            "Deserialized currentPlayingIndex: %d, currentPlayingPosition: %d, shuffle: %b",
            state.currentPlayingIndex,
            state.currentPlayingPosition,
            state.shufflePlay
        )

        return state
    }

    companion object {
        private val isSetup = AtomicBoolean(false)
        private val isSerializing = AtomicBoolean(false)
        private val isDeserializing = AtomicBoolean(false)
    }
}
