/*
 * MediaPlayerController.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.service

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.annotation.IntRange
import androidx.fragment.app.Fragment
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.OFFLINE_DB_ID
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.navigateToCurrent
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.getTrackId
import org.moire.ultrasonic.util.launchWithToast
import org.moire.ultrasonic.util.setRating
import org.moire.ultrasonic.util.setStarred
import org.moire.ultrasonic.util.toMediaItem
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

private const val CONTROLLER_SWITCH_DELAY = 500L
private const val VOLUME_DELTA = 0.05f

/**
 * The Media Player Manager can forward commands to the Media3 controller as
 * well as switch between different player interfaces (local, remote, cast etc).
 * This class contains everything that is necessary for the Application UI
 * to control the Media Player implementation.
 */
@Suppress("TooManyFunctions")
class MediaPlayerManager(
    private val playbackStateSerializer: PlaybackStateSerializer,
    private val externalStorageMonitor: ExternalStorageMonitor
) : KoinComponent {

    private var created = false
    var suggestedPlaylistName: String? = null
    var keepScreenOn = false
    private var autoPlayStart = false

    private val scrobbler = Scrobbler()

    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private var mainScope = CoroutineScope(Dispatchers.Main)

    private var sessionToken = SessionToken(
        UApp.applicationContext(),
        ComponentName(UApp.applicationContext(), PlaybackService::class.java)
    )

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private var controller: Player? = null

    private var listeners: Player.Listener = object : Player.Listener {

        /*
         * Log all events
         */
        override fun onEvents(player: Player, events: Player.Events) {
            for (i in 0 until events.size()) {
                Timber.i("Media3 Event, event type: %s", events[i])
            }
        }

        /*
         * This will be called everytime the playlist has changed.
         * We run the event through RxBus in order to throttle them
         */
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val start = timeline.getFirstWindowIndex(isShufflePlayEnabled)
            Timber.w("On timeline changed. First shuffle play at index: %s", start)
            deferredPlay?.let {
                Timber.w("Executing deferred shuffle play")
                it()
                deferredPlay = null
            }
            val playlist = Util.getPlayListFromTimeline(timeline, false).map(MediaItem::toTrack)
            RxBus.playlistPublisher.onNext(playlist)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playerStateChangedHandler()
            publishPlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerStateChangedHandler()
            publishPlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            clearBookmark()
            // TRANSITION_REASON_AUTO means that the previous track finished playing and a new one has started.
            if (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO && cachedMediaItem != null) {
                scrobbler.scrobble(cachedMediaItem?.toTrack(), true)
            }
            cachedMediaItem = mediaItem
            publishPlaybackState()
        }

        /*
         * If the same item is contained in a playlist multiple times directly after each
         * other, Media3 on emits a PositionDiscontinuity event.
         * Can be removed if https://github.com/androidx/media/issues/68 is fixed.
         */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            playerStateChangedHandler()
            publishPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.w(error.toString())
            if (!isJukeboxEnabled) return

            mainScope.launch {
                toast(
                    error.errorCode,
                    false,
                    UApp.applicationContext()
                )
            }
            isJukeboxEnabled = false
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            val timeline: Timeline = controller!!.currentTimeline
            var windowIndex = timeline.getFirstWindowIndex(true)
            var count = 0
            Timber.d("Shuffle: windowIndex: $windowIndex, at: $count")
            while (windowIndex != C.INDEX_UNSET) {
                count++
                windowIndex = timeline.getNextWindowIndex(
                    windowIndex, REPEAT_MODE_OFF, true
                )
                Timber.d("Shuffle: windowIndex: $windowIndex, at: $count")
            }
        }
    }

    private var deferredPlay: (() -> Unit)? = null

    private var cachedMediaItem: MediaItem? = null

    fun onCreate(onCreated: () -> Unit) {
        if (created) return
        externalStorageMonitor.onCreate { reset() }

        createMediaController(onCreated)

        rxBusSubscription += RxBus.activeServerChangingObservable
            // All interaction with the Media3 needs to happen on the main thread
            .subscribeOn(RxBus.mainThread())
            .subscribe { oldServer ->
                if (oldServer != OFFLINE_DB_ID) {
                    // When the server changes, the playlist can retain the downloaded songs.
                    // Incomplete songs should be removed as the new server won't recognise them.
                    removeIncompleteTracksFromPlaylist()
                    DownloadService.requestStop()
                }
                if (controller is JukeboxMediaPlayer) {
                    // When the server changes, the Jukebox should be released.
                    // The new server won't understand the jukebox requests of the old one.
                    switchToLocalPlayer()
                }
            }

        rxBusSubscription += RxBus.activeServerChangedObservable.subscribe {
            val jukebox = it.jukeboxByDefault
            // Remove all songs when changing servers before turning on Jukebox.
            // Jukebox wouldn't find the songs on the new server.
            if (jukebox) controller?.clearMediaItems()
            // Update the Jukebox state as the new server requires
            isJukeboxEnabled = jukebox
        }

        rxBusSubscription += RxBus.throttledPlaylistObservable
            // All interaction with the Media3 needs to happen on the main thread
            .subscribeOn(RxBus.mainThread())
            .subscribe {
                serializeCurrentSession()
            }

        rxBusSubscription += RxBus.throttledPlayerStateObservable
            // All interaction with the Media3 needs to happen on the main thread
            .subscribeOn(RxBus.mainThread())
            .subscribe {
                serializeCurrentSession()
            }

        rxBusSubscription += RxBus.shutdownCommandObservable.subscribe {
            clear(false)
            onDestroy()
        }

        rxBusSubscription += RxBus.stopServiceCommandObservable.subscribe {
            clear(false)
            onDestroy()
        }

        rxBusSubscription += RxBus.ratingSubmitterObservable.subscribe {
            // Ensure correct thread
            mainScope.launch {
                val mediaItem =
                    playlist.firstOrNull { item -> item.getTrackId() == it.id } ?: return@launch
                if (it.rating is HeartRating) {
                    mediaItem.setStarred(it.rating.isHeart)
                }
                if (it.rating is StarRating) {
                    mediaItem.setRating(it.rating.starRating.toInt())
                }
                mediaItem.toTrack() // Update item in Converter cache
            }
        }

        created = true
        Timber.i("MediaPlayerController started")
    }

    private fun createMediaController(onCreated: () -> Unit) {
        mediaControllerFuture = MediaController.Builder(
            UApp.applicationContext(),
            sessionToken
        )
            // Specify mainThread explicitly
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()

        mediaControllerFuture?.addListener({
            controller = mediaControllerFuture?.get()

            Timber.i("MediaController Instance received")
            controller?.addListener(listeners)
            onCreated()
            Timber.i("MediaPlayerController creation complete")
        }, MoreExecutors.directExecutor())
    }

    private fun playerStateChangedHandler() {
        val currentPlaying = controller?.currentMediaItem?.toTrack() ?: return

        when (playbackState) {
            Player.STATE_READY -> {
                if (isPlaying) {
                    scrobbler.scrobble(currentPlaying, false)
                }
            }
            // STATE_ENDED is only signaled if the whole playlist completes. Scrobble the last song.
            Player.STATE_ENDED -> {
                scrobbler.scrobble(currentPlaying, true)
            }
        }
    }

    fun addListener(listener: Player.Listener) {
        controller?.addListener(listener)
    }

    private fun clearBookmark() {
        // This method is called just before we update the cachedMediaItem,
        // so in fact cachedMediaItem will refer to the track that has just finished.
        if (cachedMediaItem != null) {
            val song = cachedMediaItem!!.toTrack()
            if (song.bookmarkPosition > 0 && Settings.shouldClearBookmark) {
                val musicService = getMusicService()
                try {
                    musicService.deleteBookmark(song.id)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun publishPlaybackState() {
        val newState = RxBus.StateWithTrack(
            track = currentMediaItem?.toTrack(),
            index = if (isShufflePlayEnabled) getCurrentShuffleIndex() else currentMediaItemIndex,
            isPlaying = isPlaying,
            state = playbackState
        )
        RxBus.playerStatePublisher.onNext(newState)
        Timber.i("New PlaybackState: %s", newState)
    }

    fun onDestroy() {
        if (!created) return

        // First stop listening to events
        rxBusSubscription.dispose()
        releaseController()

        // Shutdown the rest
        externalStorageMonitor.onDestroy()
        DownloadService.requestStop()
        created = false
        Timber.i("MediaPlayerManager destroyed")
    }

    @Synchronized
    fun restore(state: PlaybackState, autoPlay: Boolean) {
        addToPlaylist(
            state.songs,
            autoPlay = false,
            shuffle = false,
            insertionMode = InsertionMode.RESTORE
        )

        repeatMode = state.repeatMode
        isShufflePlayEnabled = state.shufflePlay

        if (state.currentPlayingIndex != -1) {
            seekTo(state.currentPlayingIndex, state.currentPlayingPosition)
            prepare()

            if (autoPlay) {
                play()
            }

            autoPlayStart = false
        }
    }

    @Synchronized
    fun play(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.prepare()
        controller?.play()
    }

    @Synchronized
    fun play() {
        controller?.prepare()
        controller?.play()
    }

    @Synchronized
    fun prepare() {
        controller?.prepare()
    }

    @Synchronized
    fun resumeOrPlay() {
        controller?.play()
    }

    @Synchronized
    fun togglePlayPause() {
        if (playbackState == Player.STATE_IDLE) autoPlayStart = true
        if (controller?.isPlaying == true) {
            controller?.pause()
        } else {
            controller?.play()
        }
    }

    @Synchronized
    fun seekTo(position: Int) {
        if (controller?.currentTimeline?.isEmpty != false) return
        Timber.i("SeekTo: %s", position)
        controller?.seekTo(position.toLong())
    }

    @Synchronized
    fun seekTo(index: Int, position: Int) {
        // This case would throw an exception in Media3. It can happen when an inconsistent state is saved.
        if (controller?.currentTimeline?.isEmpty != false ||
            index >= controller!!.currentTimeline.windowCount
        ) {
            return
        }

        Timber.i("SeekTo: %s %s", index, position)
        controller?.seekTo(index, position.toLong())
    }

    fun seekForward() {
        controller?.seekForward()
    }

    fun seekBack() {
        controller?.seekBack()
    }

    @Synchronized
    fun pause() {
        controller?.pause()
    }

    @Synchronized
    fun stop() {
        controller?.stop()
    }

    @Synchronized
    fun addToPlaylist(
        songs: List<Track>,
        autoPlay: Boolean,
        shuffle: Boolean,
        insertionMode: InsertionMode
    ) {
        var insertAt = 0

        when (insertionMode) {
            InsertionMode.CLEAR -> clear()
            InsertionMode.APPEND -> insertAt = mediaItemCount
            InsertionMode.AFTER_CURRENT -> {
                // Must never be larger than the count of items (especially when empty)
                insertAt = (currentMediaItemIndex + 1).coerceAtMost(mediaItemCount)
            }
            InsertionMode.RESTORE -> {
                if (mediaItemCount == 0) {
                    insertAt = 0
                } else {
                    Timber.i("Skipping restore: playlist already populated")
                    return
                }
            }
        }

        val mediaItems: List<MediaItem> = songs.map {
            val result = it.toMediaItem()
            result
        }

        Timber.w("Adding ${mediaItems.size} media items")
        controller?.addMediaItems(insertAt, mediaItems)

        // There is a bug in media3 ( https://github.com/androidx/media/issues/480 ),
        // so we must first add the tracks, and then enable shuffle
        if (shuffle) isShufflePlayEnabled = true

        prepare()

        // Playback doesn't start correctly when the player is in STATE_ENDED.
        // So we need to call seek before (this is what play(0,0)) does.
        // We can't just use play(0,0) then all random playlists will start with the first track.
        // Additionally the shuffle order becomes clear on after some time, so we need to wait for
        // the right event, and can start playback only then.
        if (autoPlay && controller?.isPlaying != true) {
            if (isShufflePlayEnabled) {
                deferredPlay = {
                    val start = controller?.currentTimeline
                        ?.getFirstWindowIndex(isShufflePlayEnabled) ?: 0
                    Timber.i("Deferred shuffle play starting now at index: %s", start)
                    play(start)
                }
            } else {
                play(0)
            }
        }
    }

    private suspend fun addToPlaylistAsync(
        songs: List<Track>,
        autoPlay: Boolean,
        shuffle: Boolean,
        insertionMode: InsertionMode
    ) {
        withContext(Dispatchers.Main) {
            addToPlaylist(
                songs = songs,
                autoPlay = autoPlay,
                shuffle = shuffle,
                insertionMode = insertionMode
            )
        }
    }

    @Suppress("LongParameterList")
    fun playTracksAndToast(
        fragment: Fragment,
        insertionMode: InsertionMode,
        tracks: List<Track> = listOf(),
        id: String? = null,
        name: String? = "",
        isShare: Boolean = false,
        isDirectory: Boolean = true,
        shuffle: Boolean = false,
        isArtist: Boolean = false
    ) {
        fragment.launchWithToast {
            val list: List<Track> =
                tracks.ifEmpty {
                    requireNotNull(id)
                    DownloadUtil.getTracksFromServerAsync(isArtist, id, isDirectory, name, isShare)
                }

            addToPlaylistAsync(
                songs = list,
                insertionMode = insertionMode,
                autoPlay = (insertionMode == InsertionMode.CLEAR),
                shuffle = shuffle
            )

            if (insertionMode == InsertionMode.CLEAR) {
                fragment.navigateToCurrent()
            }

            when (insertionMode) {
                InsertionMode.AFTER_CURRENT ->
                    quantize(R.plurals.n_songs_added_after_current, list)

                InsertionMode.APPEND ->
                    quantize(R.plurals.n_songs_added_to_end, list)

                InsertionMode.CLEAR, InsertionMode.RESTORE -> {
                    if (Settings.shouldTransitionOnPlayback) {
                        null
                    } else {
                        quantize(R.plurals.n_songs_added_play_now, list)
                    }
                }
            }
        }
    }

    private fun quantize(resId: Int, tracks: List<Track>): String {
        return UApp.applicationContext().resources.getQuantityString(
            resId,
            tracks.size,
            tracks.size
        )
    }

    @set:Synchronized
    var isShufflePlayEnabled: Boolean
        get() = controller?.shuffleModeEnabled == true
        set(enabled) {
            Timber.i("Shuffle is now enabled: %s", enabled)
            RxBus.shufflePlayPublisher.onNext(enabled)
            controller?.shuffleModeEnabled = enabled
        }

    @Synchronized
    fun toggleShuffle(): Boolean {
        isShufflePlayEnabled = !isShufflePlayEnabled
        return isShufflePlayEnabled
    }

    /**
     * Returns an estimate of the percentage in the current content up to which data is
     * buffered, or 0 if no estimate is available.
     */
    @get:IntRange(from = 0, to = 100)
    val bufferedPercentage: Int
        get() = controller?.bufferedPercentage ?: 0

    @Synchronized
    fun moveItemInPlaylist(oldPos: Int, newPos: Int) {
        // TODO: This currently does not care about shuffle position.
        controller?.moveMediaItem(oldPos, newPos)
    }

    @set:Synchronized
    var repeatMode: Int
        get() = controller?.repeatMode ?: 0
        set(newMode) {
            controller?.repeatMode = newMode
        }

    @Synchronized
    @JvmOverloads
    fun clear(serialize: Boolean = true) {
        controller?.clearMediaItems()

        if (controller != null && serialize) {
            playbackStateSerializer.serializeAsync(
                listOf(),
                -1,
                0,
                isShufflePlayEnabled,
                repeatMode
            )
        }
    }

    @Synchronized
    fun removeIncompleteTracksFromPlaylist() {
        val list = playlist.toList()
        var removed = 0
        for ((index, item) in list.withIndex()) {
            val state = DownloadService.getDownloadState(item.toTrack())

            // The track is not downloaded, remove it
            if (state != DownloadState.DONE && state != DownloadState.PINNED) {
                removeFromPlaylist(index - removed)
                removed++
            }
        }
    }

    @Synchronized
    fun removeFromPlaylist(position: Int) {
        controller?.removeMediaItem(position)
    }

    @Synchronized
    private fun serializeCurrentSession() {
        // Don't serialize invalid sessions
        if (currentMediaItemIndex == -1) return

        playbackStateSerializer.serializeAsync(
            songs = playlist.map { it.toTrack() },
            currentPlayingIndex = currentMediaItemIndex,
            currentPlayingPosition = playerPosition,
            isShufflePlayEnabled,
            repeatMode
        )
    }

    @Synchronized
    fun seekToPrevious() {
        controller?.seekToPrevious()
    }

    @Synchronized
    fun canSeekToPrevious(): Boolean {
        return controller?.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) == true
    }

    @Synchronized
    fun seekToNext() {
        controller?.seekToNext()
    }

    @Synchronized
    fun canSeekToNext(): Boolean {
        return controller?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) == true
    }

    @Synchronized
    fun reset() {
        controller?.clearMediaItems()
    }

    @get:Synchronized
    val playerPosition: Int
        get() {
            return controller?.currentPosition?.toInt() ?: 0
        }

    @get:Synchronized
    val playerDuration: Int
        get() {
            // Media3 will only report a duration when the file is prepared
            val reportedDuration = controller?.duration ?: C.TIME_UNSET
            if (reportedDuration != C.TIME_UNSET) return reportedDuration.toInt()
            // If Media3 doesn't know the duration yet, use the duration in the metadata
            return (currentMediaItem?.mediaMetadata?.extras?.getInt("duration") ?: 0) * 1000
        }

    val playbackState: Int
        get() = controller?.playbackState ?: 0

    val isPlaying: Boolean
        get() = controller?.isPlaying ?: false

    @set:Synchronized
    var isJukeboxEnabled: Boolean
        get() = PlaybackService.actualBackend == PlayerBackend.JUKEBOX
        set(shouldEnable) {
            if (shouldEnable) {
                switchToJukebox()
            } else {
                switchToLocalPlayer()
            }
        }

    private fun switchToJukebox() {
        if (isJukeboxEnabled) return
        scheduleSwitchTo(PlayerBackend.JUKEBOX)
        DownloadService.requestStop()
        controller?.pause()
        controller?.stop()
    }

    private fun switchToLocalPlayer() {
        if (!isJukeboxEnabled) return
        scheduleSwitchTo(PlayerBackend.LOCAL)
        controller?.stop()
    }

    private fun scheduleSwitchTo(newBackend: PlayerBackend) {
        val currentPlaylist = playlist.toList()
        val currentIndex = controller?.currentMediaItemIndex ?: 0
        val currentPosition = controller?.currentPosition ?: 0

        Handler(Looper.getMainLooper()).postDelayed({
            // Change the backend
            PlaybackService.setBackend(newBackend)
            // Restore the media items
            controller?.setMediaItems(currentPlaylist, currentIndex, currentPosition)
        }, CONTROLLER_SWITCH_DELAY)
    }

    private fun releaseController() {
        controller?.removeListener(listeners)
        controller?.release()
        if (mediaControllerFuture != null) MediaController.releaseFuture(mediaControllerFuture!!)
        Timber.i("MediaPlayerController released")
    }

    fun adjustVolume(up: Boolean) {
        val delta = if (up) VOLUME_DELTA else -VOLUME_DELTA
        var gain = controller?.volume ?: return
        gain += delta
        gain = gain.coerceAtLeast(0.0f)
        gain = gain.coerceAtMost(1.0f)
        controller?.volume = gain
    }

    /*
     * Sets the rating of the current track
     */
    private fun setRating(rating: Rating) {
        if (controller is MediaController) {
            (controller as MediaController).setRating(rating)
        }
    }

    /*
     * This legacy function simply emits a rating update,
     * which will then be processed by both the RatingManager as well as the controller
     */
    fun legacyToggleStar() {
        if (currentMediaItem == null) return
        val track = currentMediaItem!!.toTrack()
        track.starred = !track.starred
        val rating = HeartRating(track.starred)

        RxBus.ratingSubmitter.onNext(
            RatingUpdate(
                track.id,
                rating
            )
        )
    }

    /*
     * This legacy function simply emits a rating update,
     * which will then be processed by both the RatingManager as well as the controller
     */
    fun legacySetRating(num: Int) {
        if (currentMediaItem == null) return
        val track = currentMediaItem!!.toTrack()
        track.userRating = num
        val rating = StarRating(5, num.toFloat())

        RxBus.ratingSubmitter.onNext(
            RatingUpdate(
                track.id,
                rating
            )
        )
    }

    val currentMediaItem: MediaItem?
        get() = controller?.currentMediaItem

    val currentMediaItemIndex: Int
        get() = controller?.currentMediaItemIndex ?: -1

    private fun getCurrentShuffleIndex(): Int {
        val currentMediaItemIndex = controller?.currentMediaItemIndex ?: return -1
        return getShuffledIndexOf(currentMediaItemIndex)
    }

    /**
     * Loops over the timeline windows to find the entry which matches the given closure.
     *
     * @param returnWindow Determines which of the two indexes of a match to return:
     * True for the position in the playlist, False for position in the play order.
     * @param searchClosure Determines the condition which the searched for window needs to match.
     * @return the index of the window that satisfies the search condition,
     * or [C.INDEX_UNSET] if not found.
     */
    @Suppress("KotlinConstantConditions")
    private fun getWindowIndexWhere(
        returnWindow: Boolean,
        searchClosure: (Int, Int) -> Boolean
    ): Int {
        val timeline = controller?.currentTimeline!!
        var windowIndex = timeline.getFirstWindowIndex(true)
        var count = 0

        while (windowIndex != C.INDEX_UNSET) {
            val match = searchClosure(count, windowIndex)
            if (match && returnWindow) return windowIndex
            if (match && !returnWindow) return count
            count++
            windowIndex = timeline.getNextWindowIndex(
                windowIndex, REPEAT_MODE_OFF, true
            )
        }

        return C.INDEX_UNSET
    }

    /**
     * Returns the index of the shuffled position of the current playback item given its original
     * position in the unshuffled timeline.
     *
     * @param searchPosition The index of the item in the unshuffled timeline to search for
     * in the shuffled timeline.
     * @return The index of the item in the shuffled timeline, or [C.INDEX_UNSET] if not found.
     */
    private fun getShuffledIndexOf(searchPosition: Int): Int {
        return getWindowIndexWhere(false) { _, windowIndex ->
            windowIndex == searchPosition
        }
    }

    /**
     * Returns the index of the unshuffled position of the current playback item given its shuffled
     * position in the shuffled timeline.
     *
     * @param shufflePosition the index of the item in the shuffled timeline to search for in the
     * unshuffled timeline.
     * @return the index of the item in the unshuffled timeline, or [C.INDEX_UNSET] if not found.
     */
    fun getUnshuffledIndexOf(shufflePosition: Int): Int {
        return getWindowIndexWhere(true) { count, _ ->
            count == shufflePosition
        }
    }

    val mediaItemCount: Int
        get() = controller?.mediaItemCount ?: 0

    fun getMediaItemAt(index: Int): MediaItem? {
        return controller?.getMediaItemAt(index)
    }

    val playlistSize: Int
        get() = controller?.currentTimeline?.windowCount ?: 0

    val playlist: List<MediaItem>
        get() {
            return Util.getPlayListFromTimeline(controller?.currentTimeline, false)
        }

    val playlistInPlayOrder: List<MediaItem>
        get() {
            return Util.getPlayListFromTimeline(
                controller?.currentTimeline,
                controller?.shuffleModeEnabled ?: false
            )
        }

    val playListDuration: Long
        get() = playlist.fold(0) { i, file ->
            i + (file.mediaMetadata.extras?.getInt("duration") ?: 0)
        }

    init {
        Timber.i("MediaPlayerController instance initiated")
    }

    enum class InsertionMode {
        CLEAR,
        APPEND,
        AFTER_CURRENT,
        RESTORE
    }

    enum class PlayerBackend { JUKEBOX, LOCAL }
}
