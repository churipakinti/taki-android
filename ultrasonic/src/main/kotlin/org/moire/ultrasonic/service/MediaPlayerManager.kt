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
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.koin.core.component.KoinComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.app.UApp
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.OFFLINE_DB_ID
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.PerfMetrics
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
private const val PLAYBACK_CHECKPOINT_INTERVAL = 5_000L

// A single addMediaItems() call with a huge list (e.g. a several-hundred-disc box
// set) can block the main thread long enough to ANR, since Media3 requires
// MediaController calls on the app's main thread. Adding in chunks and yielding
// between them lets input dispatch keep up. See
// docs/POSIBLES_ERRORES_Y_VERIFICACION.md item 1 for the original ANR this mirrors.
// Internal (not private) because MediaLibrarySessionCallback.shuffleCurrentPlaylist() needs
// the same protection for its own addMediaItems() call - see docs/AUDITORIA_FUNCIONAMIENTO_INTERNO.md.
internal const val ADD_MEDIA_ITEMS_CHUNK_SIZE = 200

// Independent of ADD_MEDIA_ITEMS_CHUNK_SIZE above: chunking only protects our own listener
// from blocking. Media3's MediaSessionLegacyStub still serializes the *entire* Player timeline
// into one Binder transaction (MediaSessionCompat.setQueue(), for Android Auto/Bluetooth/Wear
// legacy clients) every time the timeline changes - measured to ANR for 5+ seconds with a
// 5,517-song queue (single Play All on a large album), with no app code on the blocked stack.
// There's no known way to chunk or disable that legacy broadcast, so the actual fix is capping
// how large the queue is allowed to get in the first place. Shares the value already proven
// safe for PlaybackStateSerializer's session-restore window - see MAX_QUEUE_SIZE usage there.
internal const val MAX_QUEUE_SIZE = 100

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

    // Owns the Sleep Timer's lifecycle (docs/TAKI_SLEEP_TIMER_FINAL_FEATURE.md) - reuses this
    // same headless mainScope rather than a new executor/scope, so it keeps counting across
    // Activity/Fragment destruction, rotation, backgrounding and screen-off. onExpire runs on
    // Dispatchers.Main (mainScope), same as every other Media3 call in this class.
    private val sleepTimerController = SleepTimerController(scope = mainScope) {
        controller?.pause()
        publishSleepTimerState()
    }

    private val addToPlaylistMutex = Mutex()
    private var addToPlaylistCallCount = 0
    private val addToPlaylistDuplicateGuard = DuplicateRequestGuard()

    private var sessionToken = SessionToken(
        UApp.applicationContext(),
        ComponentName(UApp.applicationContext(), PlaybackService::class.java)
    )

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private var controller: Player? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Position changes do not emit regular Media3 state events. Checkpoint while playing so an
     * abrupt process death restores close to the last audible position.
     */
    private val playbackCheckpoint = object : Runnable {
        override fun run() {
            if (isPlaying) serializeCurrentPositionCheckpoint()
            mainHandler.postDelayed(this, PLAYBACK_CHECKPOINT_INTERVAL)
        }
    }

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
            // While a bulk chunked addMediaItems() is in progress (see
            // withTimelinePublishSuppressed), this fires once per chunk with an
            // ever-growing timeline - walking and converting it every time turns an
            // O(n) publish into an O(n^2) one and is what actually freezes the UI for
            // several seconds on a large album, even though the chunking already
            // prevents a hard ANR. Skip here; withTimelinePublishSuppressed publishes
            // once with the final timeline instead.
            if (isBulkAddInProgress) return
            publishCurrentPlaylist(timeline)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playerStateChangedHandler()
            publishPlaybackState()
            // The queue reaching its natural end (no next item to auto-transition to) is the
            // other "end of track" completion path besides onMediaItemTransition below - see
            // SleepTimerController.onTrackFinishedNaturally's doc.
            if (playbackState == Player.STATE_ENDED) {
                sleepTimerController.onTrackFinishedNaturally()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) PerfMetrics.mark("playback_started")
            playerStateChangedHandler()
            publishPlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            clearBookmark()
            // TRANSITION_REASON_AUTO means that the previous track finished playing and a new one has started.
            if (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO && cachedMediaItem != null) {
                scrobbler.scrobble(cachedMediaItem?.toTrack(), true)
            }
            // Only AUTO (natural progression) and REPEAT (repeat-one looping the same track)
            // count as the current track "finishing" for the Sleep Timer's end-of-track mode.
            // MEDIA_ITEM_TRANSITION_REASON_SEEK (a manual Next/track tap) must NOT reach here -
            // see SleepTimerController.onTrackFinishedNaturally's doc for why.
            if (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                sleepTimerController.onTrackFinishedNaturally()
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
                    windowIndex,
                    REPEAT_MODE_OFF,
                    true
                )
                Timber.d("Shuffle: windowIndex: $windowIndex, at: $count")
            }
            publishPlaybackState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            publishPlaybackState()
        }
    }

    private var deferredPlay: (() -> Unit)? = null

    // Guards against the O(n^2) UI freeze described in the onTimelineChanged listener
    // above. Internal (not private) so MediaLibrarySessionCallback.shuffleCurrentPlaylist()
    // can use the same suppression for its own chunked addMediaItems() call - it shares
    // the same underlying Player and listener.
    private var isBulkAddInProgress = false

    private fun publishCurrentPlaylist(timeline: Timeline?) {
        val playlist = Util.getPlayListFromTimeline(timeline, false).map(MediaItem::toTrack)
        RxBus.playlistPublisher.onNext(playlist)
    }

    /**
     * Runs [block] with the expensive per-chunk timeline publish suppressed, then publishes
     * once with the final timeline. Use around any loop that calls addMediaItems() in chunks.
     */
    internal suspend fun withTimelinePublishSuppressed(block: suspend () -> Unit) {
        isBulkAddInProgress = true
        RxBus.queueLoadingPublisher.onNext(true)
        try {
            block()
        } finally {
            isBulkAddInProgress = false
            publishCurrentPlaylist(controller?.currentTimeline)
            RxBus.queueLoadingPublisher.onNext(false)
        }
    }

    /**
     * Synchronous counterpart of [withTimelinePublishSuppressed] for callers that aren't
     * suspend functions (e.g. [removeIncompleteTracksFromPlaylist], invoked directly from an
     * RxJava subscription). Same suppress-then-publish-once behavior; see that function's doc
     * for why this matters. removeMediaItem() is a single synchronous call with no chunking
     * concern of its own here, only the listener's per-call timeline walk.
     */
    private fun withTimelinePublishSuppressedSync(block: () -> Unit) {
        isBulkAddInProgress = true
        RxBus.queueLoadingPublisher.onNext(true)
        try {
            block()
        } finally {
            isBulkAddInProgress = false
            publishCurrentPlaylist(controller?.currentTimeline)
            RxBus.queueLoadingPublisher.onNext(false)
        }
    }

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

        // Ensures a subscriber that opens before the user ever touches the Sleep Timer still
        // gets a value from the replay(1) buffer instead of nothing.
        publishSleepTimerState()

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
            mainHandler.removeCallbacks(playbackCheckpoint)
            mainHandler.postDelayed(playbackCheckpoint, PLAYBACK_CHECKPOINT_INTERVAL)
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
        mainHandler.removeCallbacks(playbackCheckpoint)
        // Covers swipe-away/force-stop/service shutdown (this is reached via
        // shutdownCommandObservable/stopServiceCommandObservable) - cancel(), not expire(), so
        // no late pause() call races the teardown already in progress.
        sleepTimerController.cancel()
        releaseController()

        // Shutdown the rest
        externalStorageMonitor.onDestroy()
        DownloadService.requestStop()
        created = false
        Timber.i("MediaPlayerManager destroyed")
    }

    @Synchronized
    fun restore(state: PlaybackState, autoPlay: Boolean) {
        PerfMetrics.mark("session_restore_start:songs=" + state.songs.size)
        repeatMode = state.repeatMode
        isShufflePlayEnabled = state.shufflePlay

        // addToPlaylist() only launches the add - it doesn't wait for it to finish, since
        // addToPlaylistLocked() adds items in chunks with yield() between them to avoid an ANR
        // on huge queues (see ADD_MEDIA_ITEMS_CHUNK_SIZE). Calling seekTo() right after it used
        // to race that coroutine: the controller's timeline was still empty when seekTo() ran,
        // so its empty-timeline guard silently dropped the seek, and playback then defaulted to
        // index 0 once the items actually landed. Restoring a session with more than a handful
        // of songs reliably reproduced this (a 50-song queue always restored to track 0 instead
        // of the persisted index/position). Awaiting addToPlaylistLocked() in the same coroutine
        // before seeking fixes the race.
        mainScope.launch {
            addToPlaylistMutex.withLock {
                addToPlaylistLocked(
                    songs = state.songs,
                    autoPlay = false,
                    shuffle = false,
                    insertionMode = InsertionMode.APPEND,
                    startIndex = null,
                    startPositionMs = 0
                )
            }

            if (state.currentPlayingIndex != -1) {
                seekTo(state.currentPlayingIndex, state.currentPlayingPosition)
                prepare()

                if (autoPlay) {
                    play()
                }
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

    val sleepTimerState: SleepTimerState
        get() = sleepTimerController.state

    /** Arms the Sleep Timer to pause after [minutes], replacing whatever was armed before. */
    fun setSleepTimer(minutes: Int) {
        sleepTimerController.setDuration(minutes)
        publishSleepTimerState()
    }

    /** Arms the Sleep Timer to pause at the next natural end of the current track. */
    fun setSleepTimerEndOfTrack() {
        sleepTimerController.setEndOfTrack()
        publishSleepTimerState()
    }

    /** Cancels the Sleep Timer, if armed. Playback is left untouched. */
    fun cancelSleepTimer() {
        sleepTimerController.cancel()
        publishSleepTimerState()
    }

    private fun publishSleepTimerState() {
        RxBus.sleepTimerStatePublisher.onNext(sleepTimerController.state)
    }

    fun addToPlaylist(
        songs: List<Track>,
        autoPlay: Boolean,
        shuffle: Boolean,
        insertionMode: InsertionMode,
        startIndex: Int? = null,
        startPositionMs: Int = 0
    ) {
        // Guards against a rapid repeated tap re-triggering the exact same queue rebuild
        // before the first tap's request finished (see TAKI_CODE_OPTIMIZATION_PLAN.md Fase 1:
        // on a large album, this was measured to double the wait before playback starts,
        // since the second request would just discard and redo the first one's work).
        val signature =
            "$insertionMode:$startIndex:$startPositionMs:${songs.size}:" +
                "${songs.firstOrNull()?.id}:${songs.lastOrNull()?.id}"
        if (!addToPlaylistDuplicateGuard.begin(signature)) {
            PerfMetrics.mark("add_to_playlist:duplicate_skipped:$signature")
            Timber.i("Ignoring duplicate addToPlaylist call already in flight: %s", signature)
            return
        }

        val callIndex = ++addToPlaylistCallCount
        PerfMetrics.mark(
            "add_to_playlist:$callIndex:call:songs=${songs.size}:mode=$insertionMode"
        )
        mainScope.launch {
            try {
                val waitToken = PerfMetrics.start("add_to_playlist:$callIndex:mutex_wait")
                addToPlaylistMutex.withLock {
                    PerfMetrics.end("add_to_playlist:$callIndex:mutex_wait", waitToken)
                    val execToken = PerfMetrics.start("add_to_playlist:$callIndex:locked")
                    addToPlaylistLocked(
                        songs = songs,
                        autoPlay = autoPlay,
                        shuffle = shuffle,
                        insertionMode = insertionMode,
                        startIndex = startIndex,
                        startPositionMs = startPositionMs
                    )
                    PerfMetrics.end("add_to_playlist:$callIndex:locked", execToken)
                }
            } finally {
                addToPlaylistDuplicateGuard.end(signature)
            }
        }
    }

    private suspend fun addToPlaylistLocked(
        songs: List<Track>,
        autoPlay: Boolean,
        shuffle: Boolean,
        insertionMode: InsertionMode,
        startIndex: Int?,
        startPositionMs: Int
    ) {
        // If the caller wants to replace the queue with the exact list that's already loaded
        // (e.g. tapping a different track within the album/list that's currently playing),
        // there is nothing to rebuild - just move the play position. Rebuilding would mean
        // converting every Track to a MediaItem and re-inserting them all in chunks, which on
        // a large album is not free (measured: ~22s for a 5,517-song album). Shuffle is
        // excluded because the caller is explicitly asking for a fresh shuffle order. See
        // TAKI_CODE_OPTIMIZATION_PLAN.md Fase 1.
        if (insertionMode == InsertionMode.CLEAR && !shuffle && queueAlreadyMatches(songs)) {
            PerfMetrics.mark("add_to_playlist:same_queue_seek:songs=${songs.size}")
            Timber.i("addToPlaylist: queue already matches, seeking instead of rebuilding")
            startPlaybackAt(startIndex, startPositionMs, autoPlay)
            return
        }

        val (songs, startIndex) = capQueueSize(songs, startIndex)

        var insertAt = 0

        when (insertionMode) {
            InsertionMode.CLEAR -> clear()

            InsertionMode.APPEND -> insertAt = mediaItemCount

            InsertionMode.AFTER_CURRENT -> {
                // Must never be larger than the count of items (especially when empty)
                insertAt = (currentMediaItemIndex + 1).coerceAtMost(mediaItemCount)
            }
        }

        // Building MediaItems is pure CPU work with no Media3 call involved, so it's
        // safe to do off the main thread even for a list of thousands of tracks.
        val mediaItems: List<MediaItem> = withContext(Dispatchers.Default) {
            songs.map { it.toMediaItem() }
        }

        Timber.w("Adding ${mediaItems.size} media items")

        // See ADD_MEDIA_ITEMS_CHUNK_SIZE: add in chunks and yield between them instead
        // of a single addMediaItems() call, so a huge queue can't block input dispatch
        // long enough to ANR.
        var addedAt = insertAt
        withTimelinePublishSuppressed {
            for (chunk in mediaItems.chunked(ADD_MEDIA_ITEMS_CHUNK_SIZE)) {
                controller?.addMediaItems(addedAt, chunk)
                addedAt += chunk.size
                yield()
            }
        }

        // There is a bug in media3 ( https://github.com/androidx/media/issues/480 ),
        // so we must first add the tracks, and then enable shuffle
        if (shuffle) isShufflePlayEnabled = true

        prepare()

        startPlaybackAt(startIndex, startPositionMs, autoPlay)
    }

    /**
     * Caps [songs] to [MAX_QUEUE_SIZE], windowed around [startIndex] (or from the start if
     * null), adjusting [startIndex] to its new position. See MAX_QUEUE_SIZE for why - this is
     * what actually prevents the Media3 legacy-queue-broadcast ANR, not the chunking above.
     * No-op (and silent) when [songs] already fits.
     */
    private fun capQueueSize(songs: List<Track>, startIndex: Int?): Pair<List<Track>, Int?> {
        if (songs.size <= MAX_QUEUE_SIZE) return songs to startIndex

        val safeIndex = startIndex?.coerceIn(0, songs.lastIndex) ?: 0
        val start = (safeIndex - MAX_QUEUE_SIZE / 2).coerceIn(0, songs.size - MAX_QUEUE_SIZE)
        Timber.w("Limiting queue from %d to %d tracks", songs.size, MAX_QUEUE_SIZE)

        // capQueueSize() only runs on the Main dispatcher (called from addToPlaylistLocked,
        // itself inside mainScope.launch), so toast() can be called directly here.
        toast(
            UApp.applicationContext().getString(R.string.queue_size_limited, MAX_QUEUE_SIZE, songs.size),
            UApp.applicationContext()
        )

        return songs.subList(start, start + MAX_QUEUE_SIZE).toList() to
            startIndex?.let { it - start }
    }

    /**
     * True if the currently loaded queue already contains exactly [songs], in the same order.
     */
    private fun queueAlreadyMatches(songs: List<Track>): Boolean {
        if (mediaItemCount != songs.size) return false
        for (i in songs.indices) {
            if (getMediaItemAt(i)?.getTrackId() != songs[i].id) return false
        }
        return true
    }

    private fun startPlaybackAt(startIndex: Int?, startPositionMs: Int, autoPlay: Boolean) {
        if (startIndex != null) {
            // Caller wants playback to start at a specific item (optionally at a specific
            // position within it, e.g. resuming a bookmark) instead of the autoPlay/shuffle
            // behavior below.
            if (startPositionMs > 0) {
                controller?.seekTo(startIndex, startPositionMs.toLong())
                controller?.play()
            } else {
                play(startIndex)
            }
            return
        }

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

                InsertionMode.CLEAR -> {
                    if (Settings.shouldTransitionOnPlayback) {
                        null
                    } else {
                        quantize(R.plurals.n_songs_added_play_now, list)
                    }
                }
            }
        }
    }

    private fun quantize(resId: Int, tracks: List<Track>): String =
        UApp.applicationContext().resources.getQuantityString(
            resId,
            tracks.size,
            tracks.size
        )

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
        cancelSleepTimer()

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
        // Switching away from a server calls this to drop songs the new server/Offline can't
        // play. Without suppression, removing each item one by one from a large queue (e.g.
        // a several-thousand-song album, mostly not downloaded) fires onTimelineChanged once
        // per removal, each doing a full timeline walk - the exact same O(n^2) freeze as
        // withTimelinePublishSuppressed fixes for adding, except here it ran under this
        // method's own lock and produced a real ANR (see CHANGES.md, P0.4 server-switch entry).
        withTimelinePublishSuppressedSync {
            for ((index, item) in list.withIndex()) {
                val state = DownloadService.getDownloadState(item.toTrack())

                // The track is not downloaded, remove it
                if (state != DownloadState.DONE && state != DownloadState.PINNED) {
                    removeFromPlaylist(index - removed)
                    removed++
                }
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
    private fun serializeCurrentPositionCheckpoint() {
        if (currentMediaItemIndex == -1) return
        playbackStateSerializer.serializeCheckpointAsync(
            currentPlayingIndex = currentMediaItemIndex,
            currentPlayingPosition = playerPosition,
            shufflePlay = isShufflePlayEnabled,
            repeatMode = repeatMode
        )
    }

    @Synchronized
    fun serializeCurrentSessionSync() {
        if (!playbackStateSerializer.isReady || currentMediaItemIndex == -1) return

        playbackStateSerializer.serializeNow(
            tracks = playlist.map { it.toTrack() },
            currentPlayingIndex = currentMediaItemIndex,
            currentPlayingPosition = playerPosition,
            shufflePlay = isShufflePlayEnabled,
            repeatMode = repeatMode
        )
    }

    @Synchronized
    fun seekToPrevious() {
        controller?.seekToPrevious()
    }

    @Synchronized
    fun canSeekToPrevious(): Boolean =
        controller?.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) == true

    @Synchronized
    fun seekToNext() {
        controller?.seekToNext()
    }

    @Synchronized
    fun canSeekToNext(): Boolean =
        controller?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) == true

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
                windowIndex,
                REPEAT_MODE_OFF,
                true
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
    private fun getShuffledIndexOf(searchPosition: Int): Int =
        getWindowIndexWhere(false) { _, windowIndex ->
            windowIndex == searchPosition
        }

    /**
     * Returns the index of the unshuffled position of the current playback item given its shuffled
     * position in the shuffled timeline.
     *
     * @param shufflePosition the index of the item in the shuffled timeline to search for in the
     * unshuffled timeline.
     * @return the index of the item in the unshuffled timeline, or [C.INDEX_UNSET] if not found.
     */
    fun getUnshuffledIndexOf(shufflePosition: Int): Int = getWindowIndexWhere(true) { count, _ ->
        count == shufflePosition
    }

    val mediaItemCount: Int
        get() = controller?.mediaItemCount ?: 0

    fun getMediaItemAt(index: Int): MediaItem? = controller?.getMediaItemAt(index)

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
        AFTER_CURRENT
    }

    enum class PlayerBackend { JUKEBOX, LOCAL }
}
