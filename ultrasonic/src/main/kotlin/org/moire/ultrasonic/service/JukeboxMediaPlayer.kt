/*
 * JukeboxMediaPlayer.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Size
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.ApiNotSupportedException
import org.moire.ultrasonic.api.subsonic.SubsonicRESTException
import org.moire.ultrasonic.app.UApp.Companion.applicationContext
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.domain.JukeboxStatus
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.sleepQuietly
import timber.log.Timber

private const val STATUS_UPDATE_INTERVAL_SECONDS = 5L
private const val QUEUE_POLL_INTERVAL_SECONDS = 1L

/**
 * Provides an asynchronous interface to the remote jukebox on the Subsonic server.
 *
 * TODO: Report warning if queue fills up.
 * TODO: Disable repeat.
 * TODO: Persist RC state?
 * TODO: Minimize status updates.
 */
@Suppress("TooManyFunctions", "DeprecatedCallableAddReplaceWith")
@SuppressLint("UnsafeOptInUsageError")
class JukeboxMediaPlayer :
    JukeboxUnimplementedFunctions(),
    Player {
    private val tasks = TaskQueue()
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private var statusUpdateFuture: ScheduledFuture<*>? = null
    private val timeOfLastUpdate = AtomicLong()
    private var jukeboxStatus: JukeboxStatus? = null
    private var previousJukeboxStatus: JukeboxStatus? = null
    private var gain = (MAX_GAIN / 3)
    private val floatGain: Float
        get() = gain.toFloat() / MAX_GAIN

    private var serviceThread: Thread? = null

    private var listeners: ListenerSet<Player.Listener>
    private val playlist: MutableList<MediaItem> = mutableListOf()

    private var _currentIndex: Int = 0
    private var currentIndex: Int
        get() = _currentIndex
        set(value) {
            // This must never be smaller 0
            _currentIndex = if (value >= 0) value else 0
        }

    companion object {
        // This is quite important, by setting the DeviceInfo the player is recognized by
        // Android as being a remote playback surface
        val DEVICE_INFO = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(10)
            .build()
        val running = AtomicBoolean()
        const val MAX_GAIN = 10
    }

    init {
        running.set(true)

        listeners = ListenerSet(
            applicationLooper,
            Clock.DEFAULT
        ) { listener: Player.Listener, flags: FlagSet? ->
            listener.onEvents(
                this,
                Player.Events(
                    flags!!
                )
            )
        }
        tasks.clear()
        updatePlaylist()
        stop()
        startProcessTasks()
    }

    @Suppress("MagicNumber")
    override fun release() {
        tasks.clear()
        stop()

        if (!running.get()) return
        running.set(false)

        serviceThread?.join()

        Timber.d("Stopped Jukebox Service")
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun getCurrentMediaItem(): MediaItem? {
        if (playlist.isEmpty()) return null
        if (currentIndex < 0 || currentIndex >= playlist.size) return null
        return playlist[currentIndex]
    }

    override fun getCurrentMediaItemIndex(): Int = currentIndex

    override fun getCurrentPeriodIndex(): Int = currentIndex

    override fun getContentPosition(): Long = currentPosition

    override fun play() {
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        startStatusUpdate()
        tasks.add(Start())
    }

    override fun seekTo(positionMs: Long) {
        seekTo(currentIndex, positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        tasks.remove(Skip::class.java)
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        startStatusUpdate()
        val positionSeconds = (positionMs / 1000).toInt()
        if (jukeboxStatus != null) {
            jukeboxStatus!!.positionSeconds = positionSeconds
        }
        tasks.add(Skip(mediaItemIndex, positionSeconds))
        currentIndex = mediaItemIndex
        updateAvailableCommands()
    }

    override fun seekBack() {
        seekTo(
            0L.coerceAtMost(
                (jukeboxStatus?.positionSeconds ?: 0) -
                    Settings.seekIntervalMillis
            )
        )
    }

    override fun seekForward() {
        seekTo((jukeboxStatus?.positionSeconds ?: 0) + Settings.seekIntervalMillis)
    }

    override fun isCurrentMediaItemSeekable() = true

    override fun isCurrentMediaItemLive() = false

    override fun prepare() {}

    override fun isPlaying(): Boolean = jukeboxStatus?.isPlaying ?: false

    override fun getPlaybackState(): Int = when (jukeboxStatus?.isPlaying) {
        true -> Player.STATE_READY
        null, false -> Player.STATE_IDLE
    }

    override fun getAvailableCommands(): Player.Commands {
        val commandsBuilder = Player.Commands.Builder().addAll(
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS
        )
        if (isPlaying) commandsBuilder.add(Player.COMMAND_STOP)
        if (playlist.isNotEmpty()) {
            commandsBuilder.addAll(
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                // Seeking back is always available
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
            if (currentIndex < playlist.size - 1) {
                commandsBuilder.addAll(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                )
            }
        }
        return commandsBuilder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    private fun updateAvailableCommands() {
        Handler(Looper.getMainLooper()).post {
            listeners.sendEvent(
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED
            ) { listener: Player.Listener ->
                listener.onAvailableCommandsChanged(
                    availableCommands
                )
            }
        }
    }

    override fun getPlayWhenReady(): Boolean = isPlaying

    override fun pause() {
        stop()
    }

    override fun stop() {
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        stopStatusUpdate()
        tasks.add(Stop())
    }

    override fun getCurrentTimeline(): Timeline = PlaylistTimeline(playlist)

    override fun getMediaItemCount(): Int = playlist.size

    override fun getMediaItemAt(index: Int): MediaItem {
        if (playlist.size == 0) return MediaItem.EMPTY
        if (index < 0 || index >= playlist.size) return MediaItem.EMPTY
        return playlist[index]
    }

    override fun getShuffleModeEnabled(): Boolean = false

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        setDeviceVolume(volume, 0)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        gain = volume
        tasks.remove(SetGain::class.java)
        tasks.add(SetGain(floatGain))

        // We must trigger an event so that the Controller knows the new volume
        Handler(Looper.getMainLooper()).post {
            listeners.queueEvent(Player.EVENT_DEVICE_VOLUME_CHANGED) {
                it.onDeviceVolumeChanged(
                    gain,
                    false
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        increaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI)
    }

    override fun increaseDeviceVolume(flags: Int) {
        gain = (gain + 1).coerceAtMost(MAX_GAIN)
        @Suppress("DEPRECATION")
        deviceVolume = gain
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        decreaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI)
    }

    override fun decreaseDeviceVolume(flags: Int) {
        gain = (gain - 1).coerceAtLeast(0)
        @Suppress("DEPRECATION")
        deviceVolume = gain
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
        setDeviceMuted(muted, C.VOLUME_FLAG_SHOW_UI)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        gain = 0
        @Suppress("DEPRECATION")
        deviceVolume = gain
    }

    override fun mute() {
        setDeviceMuted(true, C.VOLUME_FLAG_SHOW_UI)
    }

    override fun unmute() {
        setDeviceMuted(false, C.VOLUME_FLAG_SHOW_UI)
    }

    override fun getVolume(): Float = floatGain

    override fun getDeviceVolume(): Int = gain

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        playlist.addAll(index, mediaItems)
        updatePlaylist()
    }

    override fun getBufferedPercentage(): Int = 0

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        if (playlist.size == 0) return
        if (currentIndex < 0 || currentIndex >= playlist.size) return
        if (newIndex < 0 || newIndex >= playlist.size) return

        val insertIndex = if (newIndex < currentIndex) newIndex else (newIndex - 1).coerceAtLeast(0)
        val item = playlist.removeAt(currentIndex)
        playlist.add(insertIndex, item)
        updatePlaylist()
    }

    override fun removeMediaItem(index: Int) {
        if (playlist.size == 0) return
        if (index < 0 || index >= playlist.size) return
        playlist.removeAt(index)
        updatePlaylist()
    }

    override fun clearMediaItems() {
        playlist.clear()
        currentIndex = 0
        updatePlaylist()
    }

    override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF

    override fun setRepeatMode(repeatMode: Int) {}

    override fun getCurrentPosition(): Long = positionSeconds * 1000L

    override fun getDuration(): Long {
        if (playlist.isEmpty()) return 0
        if (currentIndex < 0 || currentIndex >= playlist.size) return 0

        return (playlist[currentIndex].mediaMetadata.extras?.getInt("duration") ?: 0)
            .toLong() * 1000
    }

    override fun getContentDuration(): Long = duration

    override fun getMediaMetadata(): MediaMetadata {
        if (playlist.isEmpty()) return MediaMetadata.EMPTY
        if (currentIndex < 0 || currentIndex >= playlist.size) return MediaMetadata.EMPTY

        return playlist[currentIndex].mediaMetadata
    }

    override fun seekToNext() {
        if (currentIndex < 0 || currentIndex >= playlist.size) return
        currentIndex++
        seekTo(currentIndex, 0)
    }

    override fun seekToPrevious() {
        if ((jukeboxStatus?.positionSeconds ?: 0) > (Settings.seekIntervalMillis)) {
            seekTo(currentIndex, 0)
            return
        }
        if (currentIndex <= 0) return
        currentIndex--
        seekTo(currentIndex, 0)
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        playlist.clear()
        playlist.addAll(mediaItems)
        updatePlaylist()
        seekTo(startIndex, startPositionMs)
    }

    private fun startProcessTasks() {
        serviceThread = object : Thread() {
            override fun run() {
                processTasks()
            }
        }
        (serviceThread as Thread).start()
    }

    @Synchronized
    private fun startStatusUpdate() {
        stopStatusUpdate()
        val updateTask = Runnable {
            tasks.remove(GetStatus::class.java)
            tasks.add(GetStatus())
        }
        statusUpdateFuture = executorService.scheduleWithFixedDelay(
            updateTask,
            STATUS_UPDATE_INTERVAL_SECONDS,
            STATUS_UPDATE_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    @Synchronized
    private fun stopStatusUpdate() {
        if (statusUpdateFuture != null) {
            statusUpdateFuture!!.cancel(false)
            statusUpdateFuture = null
        }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun processTasks() {
        Timber.d("JukeboxMediaPlayer processTasks starting")
        while (running.get()) {
            // Sleep a bit to spare processor time if we loop a lot
            sleepQuietly(10)
            // This is only necessary if Ultrasonic goes offline sooner than the thread stops
            if (isOffline()) continue
            var task: JukeboxTask? = null
            try {
                task = tasks.poll() ?: continue
                Timber.v("JukeBoxMediaPlayer processTasks processes Task %s", task::class)
                val status = task.execute()
                onStatusUpdate(status)
            } catch (all: Throwable) {
                onError(task, all)
            }
        }
        Timber.d("JukeboxMediaPlayer processTasks stopped")
    }

    // Jukebox status contains data received from the server, we need to validate it!
    private fun onStatusUpdate(jukeboxStatus: JukeboxStatus) {
        timeOfLastUpdate.set(System.currentTimeMillis())
        previousJukeboxStatus = this.jukeboxStatus
        this.jukeboxStatus = jukeboxStatus
        var shouldUpdateCommands = false

        // Ensure that the index is never smaller than 0
        // If -1 assume that this means we are not playing
        if (jukeboxStatus.currentPlayingIndex != null && jukeboxStatus.currentPlayingIndex!! < 0) {
            jukeboxStatus.currentPlayingIndex = 0
            jukeboxStatus.isPlaying = false
        }
        currentIndex = jukeboxStatus.currentPlayingIndex ?: currentIndex

        if (jukeboxStatus.isPlaying != previousJukeboxStatus?.isPlaying) {
            shouldUpdateCommands = true
            Handler(Looper.getMainLooper()).post {
                listeners.queueEvent(Player.EVENT_PLAYBACK_STATE_CHANGED) {
                    it.onPlaybackStateChanged(
                        if (jukeboxStatus.isPlaying) Player.STATE_READY else Player.STATE_IDLE
                    )
                }

                listeners.queueEvent(Player.EVENT_IS_PLAYING_CHANGED) {
                    it.onIsPlayingChanged(jukeboxStatus.isPlaying)
                }
            }
        }

        if (jukeboxStatus.currentPlayingIndex != previousJukeboxStatus?.currentPlayingIndex) {
            shouldUpdateCommands = true
            currentIndex = jukeboxStatus.currentPlayingIndex ?: 0
            val currentMedia =
                if (currentIndex > 0 && currentIndex < playlist.size) {
                    playlist[currentIndex]
                } else {
                    MediaItem.EMPTY
                }

            Handler(Looper.getMainLooper()).post {
                listeners.queueEvent(Player.EVENT_MEDIA_ITEM_TRANSITION) {
                    it.onMediaItemTransition(
                        currentMedia,
                        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                    )
                }
            }
        }

        if (shouldUpdateCommands) updateAvailableCommands()

        Handler(Looper.getMainLooper()).post {
            listeners.flushEvents()
        }
    }

    private fun onError(task: JukeboxTask?, x: Throwable) {
        var exception: PlaybackException? = null
        if (x is ApiNotSupportedException && task !is Stop) {
            exception = PlaybackException(
                "Jukebox server too old",
                null,
                R.string.download_jukebox_server_too_old
            )
        } else if (x is OfflineException && task !is Stop) {
            exception = PlaybackException(
                "Jukebox offline",
                null,
                R.string.download_jukebox_offline
            )
        } else if (x is SubsonicRESTException && x.code == 50 && task !is Stop) {
            exception = PlaybackException(
                "Jukebox not authorized",
                null,
                R.string.download_jukebox_not_authorized
            )
        }

        if (exception != null) {
            Handler(Looper.getMainLooper()).post {
                listeners.sendEvent(Player.EVENT_PLAYER_ERROR) {
                    it.onPlayerError(exception)
                }
            }
        } else {
            Timber.e(x, "Failed to process jukebox task")
        }
    }

    private fun updatePlaylist() {
        if (!running.get()) return
        tasks.remove(Skip::class.java)
        tasks.remove(Stop::class.java)
        tasks.remove(Start::class.java)
        val ids: MutableList<String> = ArrayList()
        for (item in playlist) {
            ids.add(item.mediaId)
        }

        if (ids.isNotEmpty()) {
            tasks.add(SetPlaylist(ids))
        } else {
            tasks.add(ClearPlaylist())
        }

        Handler(Looper.getMainLooper()).post {
            listeners.sendEvent(
                Player.EVENT_TIMELINE_CHANGED
            ) { listener: Player.Listener ->
                listener.onTimelineChanged(
                    PlaylistTimeline(playlist),
                    Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
                )
            }
        }
    }

    private val musicService: MusicService
        get() = getMusicService()

    private val positionSeconds: Int
        get() {
            if (jukeboxStatus == null ||
                jukeboxStatus!!.positionSeconds == null ||
                timeOfLastUpdate.get() == 0L
            ) {
                return 0
            }
            if (jukeboxStatus!!.isPlaying) {
                val secondsSinceLastUpdate =
                    ((System.currentTimeMillis() - timeOfLastUpdate.get()) / 1000L).toInt()
                return jukeboxStatus!!.positionSeconds!! + secondsSinceLastUpdate
            }
            return jukeboxStatus!!.positionSeconds!!
        }

    private class TaskQueue {
        private val queue = LinkedBlockingQueue<JukeboxTask>()
        fun add(jukeboxTask: JukeboxTask) {
            queue.add(jukeboxTask)
        }

        fun poll(): JukeboxTask? = queue.poll(QUEUE_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)

        fun remove(taskClass: Class<out JukeboxTask?>) {
            try {
                val iterator = queue.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()
                    if (taskClass == task.javaClass) {
                        iterator.remove()
                    }
                }
            } catch (x: Throwable) {
                Timber.w(x, "Failed to clean-up task queue.")
            }
        }

        fun clear() {
            queue.clear()
        }
    }

    private abstract class JukeboxTask {
        @Throws(Exception::class)
        abstract fun execute(): JukeboxStatus
        override fun toString(): String = javaClass.simpleName
    }

    private inner class GetStatus : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus = musicService.getJukeboxStatus()
    }

    private inner class SetPlaylist(private val ids: List<String>) : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus = musicService.updateJukeboxPlaylist(ids)
    }

    private inner class Skip(private val index: Int, private val offsetSeconds: Int) :
        JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus = musicService.skipJukebox(index, offsetSeconds)
    }

    private inner class Stop : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus = musicService.stopJukebox()
    }

    private inner class ClearPlaylist : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus = musicService.clearJukebox()
    }

    private inner class Start : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus = musicService.startJukebox()
    }

    private inner class SetGain(private val gain: Float) : JukeboxTask() {
        @Throws(Exception::class)
        override fun execute(): JukeboxStatus = musicService.setJukeboxGain(gain)
    }

    // The constants below are necessary so a MediaSession can be built from the Jukebox Service
    override fun isCurrentMediaItemDynamic(): Boolean = false

    override fun getTrackSelectionParameters(): TrackSelectionParameters =
        TrackSelectionParameters.DEFAULT

    override fun getMaxSeekToPreviousPosition(): Long = Settings.seekInterval.toLong()

    override fun getSeekBackIncrement(): Long = Settings.seekInterval.toLong()

    override fun getSeekForwardIncrement(): Long = Settings.seekInterval.toLong()

    override fun isLoading(): Boolean = false

    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE

    override fun isDeviceMuted(): Boolean = false

    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO

    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT

    override fun setVolume(volume: Float) {}

    override fun getVideoSize(): VideoSize = VideoSize(0, 0)

    override fun getSurfaceSize(): Size = Size(0, 0)

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getCurrentLiveOffset(): Long = C.TIME_UNSET

    override fun getTotalBufferedDuration(): Long = 0

    override fun isPlayingAd(): Boolean = false

    override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET

    override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET

    override fun canAdvertiseSession(): Boolean = true

    override fun getApplicationLooper(): Looper = applicationContext().mainLooper

    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY

    override fun getDeviceInfo(): DeviceInfo = DEVICE_INFO

    override fun getPlayerError(): PlaybackException? = null

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters(1F, 1F)

    override fun getBufferedPosition(): Long = 0
}
