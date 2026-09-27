/*
 * PlayerFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.HeartRating
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Date
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeFragment
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.shouldUseId3Tags
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.ui.player.NowPlayingActions
import org.moire.ultrasonic.ui.player.NowPlayingOverflowItem
import org.moire.ultrasonic.ui.player.NowPlayingScreen
import org.moire.ultrasonic.ui.player.SleepTimerActions
import org.moire.ultrasonic.ui.player.SleepTimerSheet
import org.moire.ultrasonic.ui.upnext.QueueEntry
import org.moire.ultrasonic.ui.upnext.UpNextActions
import org.moire.ultrasonic.ui.upnext.UpNextCurrentUi
import org.moire.ultrasonic.ui.upnext.UpNextMenuItem
import org.moire.ultrasonic.ui.upnext.UpNextScreen
import org.moire.ultrasonic.ui.upnext.UpNextUiState
import org.moire.ultrasonic.ui.upnext.buildUpcoming
import org.moire.ultrasonic.ui.playback.PlaybackProgress
import org.moire.ultrasonic.ui.playback.PlaybackUiStateHolder
import org.moire.ultrasonic.ui.playback.PlayerUiState
import org.moire.ultrasonic.ui.playback.RepeatMode
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.applyTheme
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.launchWithToast
import org.moire.ultrasonic.util.toTrack
import timber.log.Timber

/**
 * Now Playing (issue #10 phase 4J): a thin Compose host over [NowPlayingScreen]. Playback stays
 * exactly where it was - [PlaybackUiStateHolder] projects [MediaPlayerManager] state, and every
 * action dispatches back through it or straight to `MediaPlayerManager`/`RxBus`/the existing
 * dialogs, never the other way. The queue is the Compose Up Next surface (issue #10 phase 4K1),
 * shown in place of Now Playing while [showUpNext] is set.
 */
@Suppress("TooManyFunctions")
class PlayerFragment :
    ScopeFragment(),
    CoroutineScope {

    private var mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    override val coroutineContext: CoroutineContext
        get() = mainScope.coroutineContext

    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val playbackUiStateHolder: PlaybackUiStateHolder by inject()
    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()
    private var isEqualizerAvailable by mutableStateOf(false)

    /**
     * The optimistic heart flip (issue #15's established pattern, e.g.
     * `AlbumDetailViewModel.setStarredOptimistic`): [PlayerUiState.isCurrentTrackLiked] is a
     * pure projection of the last `RxBus.playerStateObservable` emission, which a rating change
     * alone does not re-fire (that goes through the separate `ratingPublished` observable, used
     * elsewhere only for the failure toast) - without this, the heart would not visibly flip
     * until some unrelated player event happened to fire next. `first` is the track id the
     * override applies to, so a track change (a real new `state.trackId`) drops it automatically
     * without needing an explicit clear. Reverted by the failure-reconciliation subscription in
     * [onViewCreated] on a rejected submission, exactly like the legacy `currentSong.starred`
     * revert.
     */
    private var favoriteOverride by mutableStateOf<Pair<String, Boolean>?>(null)

    /** Kept only for the favourite-failure reconciliation toast, which is keyed on the track id
     *  that was actually submitted - exactly the legacy `currentSong?.id` check. */
    private var pendingFavoriteTrackId: String? = null

    /** Whether the Up Next surface (the queue) shows instead of Now Playing - a player mode, not
     *  a navigation destination (issue #10 phase 4K1). System back closes it first. */
    private val showUpNext = mutableStateOf(false)
    private var upNextBackCallback: OnBackPressedCallback? = null

    /** Whether the Sleep Timer sheet is open - transient UI over Now Playing/Up Next, not a
     *  navigation destination (issue #10 phase 4K6). */
    private val showSleepTimer = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        mainScope = CoroutineScope(Dispatchers.Main)
        showUpNext.value = false
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    val state by playbackUiStateHolder.playerState.collectAsStateWithLifecycle()
                    val sleepTimerState by playbackUiStateHolder.sleepTimerState
                        .collectAsStateWithLifecycle()
                    var progress by remember { mutableStateOf(PlaybackProgress()) }

                    ProgressTicker(isPlaying = state.isPlaying, onTick = { progress = it })

                    val displayState = favoriteOverride?.let { (trackId, liked) ->
                        if (trackId == state.trackId) state.copy(isCurrentTrackLiked = liked) else null
                    } ?: state

                    Box(Modifier.fillMaxSize()) {
                        if (showUpNext.value) {
                            UpNextHost(
                                displayState = displayState,
                                progressFraction = {
                                    if (progress.durationMs > 0) {
                                        progress.positionMs.toFloat() / progress.durationMs
                                    } else {
                                        0f
                                    }
                                },
                            )
                        } else {
                            NowPlayingScreen(
                                state = displayState,
                                progress = progress,
                                sleepTimerState = sleepTimerState,
                                actions = nowPlayingActions(
                                    onOpenUpNext = { setUpNextOpen(true) },
                                    currentlyLiked = displayState.isCurrentTrackLiked,
                                ),
                            )
                        }
                        SleepTimerSheet(
                            visible = showSleepTimer.value,
                            state = sleepTimerState,
                            hasCurrentTrack = displayState.hasCurrentTrack,
                            actions = sleepTimerActions(),
                        )
                    }
                }
            }
        }
    }

    /** The legacy `executorService.scheduleWithFixedDelay(..., 0L, 500L, ...)`, ported to a
     *  lifecycle-aware Compose effect: polls the transport snapshot every 500ms while the
     *  screen is at least STARTED, same cadence, same on/off boundary. */
    @Composable
    private fun ProgressTicker(isPlaying: Boolean, onTick: (PlaybackProgress) -> Unit) {
        LaunchedEffect(Unit) {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    onTick(playbackUiStateHolder.snapshotProgress())
                    delay(PROGRESS_TICK_MS)
                }
            }
        }
        // Re-read once immediately on a play/pause flip, instead of waiting up to 500ms for the
        // next tick, so the primary button's icon and an unpaused seek bar feel instant.
        LaunchedEffect(isPlaying) {
            onTick(playbackUiStateHolder.snapshotProgress())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        upNextBackCallback = object : OnBackPressedCallback(showUpNext.value) {
            override fun handleOnBackPressed() = setUpNextOpen(false)
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }

        EqualizerController.get().observe(requireActivity()) { controller ->
            isEqualizerAvailable = controller != null
        }

        rxBusSubscription += RxBus.ratingPublishedObservable.subscribe { update ->
            if (update.id != pendingFavoriteTrackId || update.success != false) return@subscribe
            favoriteOverride = favoriteOverride?.let { (trackId, liked) ->
                if (trackId == update.id) trackId to !liked else null
            }
            toast(R.string.now_playing_favorite_failed)
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn()
    }

    override fun onDestroyView() {
        rxBusSubscription.dispose()
        cancel("CoroutineScope cancelled because the view was destroyed")
        super.onDestroyView()
    }

    private fun applyKeepScreenOn() {
        val window = requireActivity().window
        if (mediaPlayerManager.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // --- Actions --------------------------------------------------------------------------

    private fun nowPlayingActions(onOpenUpNext: () -> Unit, currentlyLiked: Boolean): NowPlayingActions {
        val currentTrack = { mediaPlayerManager.currentMediaItem?.toTrack() }
        return NowPlayingActions(
            onBack = { findNavController().navigateUp() },
            onPlayPause = {
                launch(CommunicationError.getHandler(context)) { playbackUiStateHolder.onPlayPause() }
            },
            onStop = {
                launch(CommunicationError.getHandler(context)) { playbackUiStateHolder.onStop() }
            },
            onPrevious = { skip(playbackUiStateHolder::onPrevious) },
            onNext = { skip(playbackUiStateHolder::onNext) },
            onSeekBackRepeat = {
                launch(CommunicationError.getHandler(context)) { playbackUiStateHolder.onSeekBack() }
            },
            onSeekForwardRepeat = {
                launch(CommunicationError.getHandler(context)) { playbackUiStateHolder.onSeekForward() }
            },
            onSeekTo = { ms ->
                launch(CommunicationError.getHandler(context)) { playbackUiStateHolder.onSeekTo(ms) }
            },
            onToggleShuffle = ::toggleShuffle,
            onCycleRepeat = ::cycleRepeat,
            onToggleFavorite = { toggleFavorite(currentTrack(), currentlyLiked) },
            onTitleClick = { goToAlbum(currentTrack()) },
            onArtistClick = { goToArtist(currentTrack()) },
            onSavePlaylist = { offerSavePlaylist() },
            onLyrics = { goToLyrics(currentTrack()) },
            onOpenUpNext = onOpenUpNext,
            onSleepTimer = { showSleepTimer.value = true },
            onOverflowItem = { item -> handleOverflow(item, currentTrack()) },
            equalizerAvailable = { isEqualizerAvailable },
            keepScreenOnActive = { mediaPlayerManager.keepScreenOn },
            onArtworkSwipeNext = { fling(playbackUiStateHolder::onNext) },
            onArtworkSwipePrevious = { fling(playbackUiStateHolder::onPrevious) },
            onArtworkSwipeSeekForward = { flingSeek(SEEK_FORWARD_MS) },
            onArtworkSwipeSeekBack = { flingSeek(-SEEK_BACK_MS) },
        )
    }

    /** Previous/Next from the transport buttons: network-checked, then dispatched through the
     *  same error-toasting `launch` every other transport command uses. */
    private fun skip(command: () -> Unit) {
        networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
        launch(CommunicationError.getHandler(context)) { command() }
    }

    /** Previous/Next from an artwork swipe: network-checked, but dispatched synchronously - the
     *  legacy `onFling` never wrapped these in the error-toasting `launch`. */
    private fun fling(command: () -> Unit) {
        networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
        command()
    }

    private fun flingSeek(deltaMs: Int) {
        networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
        val position = playbackUiStateHolder.snapshotProgress().positionMs.toInt()
        playbackUiStateHolder.onSeekTo(position + deltaMs)
    }

    private fun toggleShuffle() {
        val enabled = playbackUiStateHolder.onToggleShuffle()
        toast(if (enabled) R.string.download_menu_shuffle_on else R.string.download_menu_shuffle_off)
    }

    private fun cycleRepeat() {
        when (playbackUiStateHolder.onCycleRepeat()) {
            RepeatMode.OFF -> toast(R.string.download_repeat_off)
            RepeatMode.ONE -> toast(R.string.download_repeat_single)
            RepeatMode.ALL -> toast(R.string.download_repeat_all)
        }
    }

    private fun handleOverflow(item: NowPlayingOverflowItem, track: Track?) {
        when (item) {
            NowPlayingOverflowItem.GO_TO_ARTIST -> goToArtist(track)
            NowPlayingOverflowItem.GO_TO_ALBUM -> goToAlbum(track)
            NowPlayingOverflowItem.SAVE_PLAYLIST -> offerSavePlaylist()
            NowPlayingOverflowItem.CLEAR_PLAYLIST -> playbackUiStateHolder.onClearQueue()
            NowPlayingOverflowItem.LYRICS -> goToLyrics(track)
            NowPlayingOverflowItem.EQUALIZER -> findNavController().navigate(R.id.playerToEqualizer)
            NowPlayingOverflowItem.TOGGLE_SCREEN_ON -> {
                mediaPlayerManager.keepScreenOn = !mediaPlayerManager.keepScreenOn
                applyKeepScreenOn()
            }
        }
    }

    private fun goToArtist(track: Track?) {
        if (track == null) return
        val artistId = track.artistId?.takeIf { it.isNotBlank() }
        if (Settings.id3TagsEnabledOnline && artistId != null) {
            findNavController().navigate(
                NavigationGraphDirections.toArtistDetail(
                    artistId = artistId,
                    artistName = track.artist.orEmpty(),
                    artistCoverArt = null,
                ),
            )
        } else if (Settings.id3TagsEnabledOnline) {
            findNavController().navigate(
                PlayerFragmentDirections.playerToAlbumsList(
                    type = AlbumListType.SORTED_BY_NAME,
                    byArtist = true,
                    id = track.artistId,
                    title = track.artist,
                    offset = 0,
                    size = 1000,
                ),
            )
        }
    }

    private fun goToAlbum(track: Track?) {
        if (track == null) return
        val albumId = if (shouldUseId3Tags()) track.albumId else track.parent
        findNavController().navigate(
            PlayerFragmentDirections.playerToSelectAlbum(
                id = albumId,
                name = track.album,
                parentId = track.parent,
                isAlbum = true,
            ),
        )
    }

    private fun goToLyrics(track: Track?) {
        if (isOffline()) {
            toast(R.string.download_lyrics_offline)
            return
        }
        if (track?.artist == null || track.title == null) return
        findNavController().navigate(
            PlayerFragmentDirections.playerToLyrics(track.artist!!, track.title!!, track.id),
        )
    }

    private fun toggleFavorite(track: Track?, currentlyLiked: Boolean) {
        if (track == null) return
        val newState = !currentlyLiked
        pendingFavoriteTrackId = track.id
        favoriteOverride = track.id to newState
        RxBus.ratingSubmitter.onNext(RatingUpdate(track.id, HeartRating(newState)))
    }

    private fun offerSavePlaylist() {
        if (mediaPlayerManager.playlistSize > 0) showSavePlaylistDialog()
    }

    @SuppressLint("InflateParams")
    private fun showSavePlaylistDialog() {
        val layout = LayoutInflater.from(this.context).inflate(R.layout.save_playlist, null)
        val playlistNameView = layout.findViewById<EditText>(R.id.save_playlist_name)

        val builder = ConfirmationDialog.Builder(requireContext())
        builder.setTitle(R.string.download_playlist_title)
        builder.setMessage(R.string.download_playlist_name)
        builder.setPositiveButton(R.string.common_save) { _, _ ->
            savePlaylistInBackground(playlistNameView.text.toString())
        }
        builder.setNegativeButton(R.string.common_cancel) { dialog, _ -> dialog.cancel() }
        builder.setView(layout)
        builder.setCancelable(true)
        val dialog = builder.create()
        val playlistName = mediaPlayerManager.suggestedPlaylistName
        if (playlistName != null) {
            playlistNameView.setText(playlistName)
        } else {
            playlistNameView.setText(DateFormat.format("yyyy-MM-dd", Date()))
        }
        dialog.show()
    }

    private fun savePlaylistInBackground(playlistName: String) {
        toast(resources.getString(R.string.download_playlist_saving, playlistName))
        mediaPlayerManager.suggestedPlaylistName = playlistName
        val entries = mediaPlayerManager.playlist.map { it.toTrack() }

        launchWithToast {
            try {
                withContext(Dispatchers.IO) {
                    MusicServiceFactory.getMusicService().createPlaylist(null, playlistName, entries)
                }
                resources.getString(R.string.download_playlist_done)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (all: Exception) {
                Timber.e(all, "Exception has occurred in savePlaylistInBackground")
                String.format(
                    Locale.ROOT,
                    "%s %s",
                    resources.getString(R.string.download_playlist_error),
                    CommunicationError.getErrorMessage(all),
                )
            }
        }
    }

    /**
     * Sleep timer sheet actions (issue #10 phase 4K6): dispatches straight to the unchanged
     * `PlaybackUiStateHolder` / `SleepTimerController` path the legacy single-choice
     * `AlertDialog` used, then dismisses the sheet - the same "select and immediately apply"
     * behavior, and the same confirmation toasts, the legacy dialog had.
     */
    private fun sleepTimerActions(): SleepTimerActions = SleepTimerActions(
        onSelectDuration = { minutes ->
            playbackUiStateHolder.onSetSleepTimer(minutes)
            toast(resources.getQuantityString(R.plurals.sleep_timer_confirm_minutes, minutes, minutes))
            showSleepTimer.value = false
        },
        onSelectEndOfTrack = {
            playbackUiStateHolder.onSetSleepTimerEndOfTrack()
            toast(R.string.sleep_timer_confirm_end_of_song)
            showSleepTimer.value = false
        },
        onCancel = {
            playbackUiStateHolder.onCancelSleepTimer()
            toast(R.string.sleep_timer_confirm_cancelled)
            showSleepTimer.value = false
        },
        onDismiss = { showSleepTimer.value = false },
    )

    // --- Up Next (issue #10 phase 4K1) -----------------------------------------------------

    private fun setUpNextOpen(open: Boolean) {
        showUpNext.value = open
        upNextBackCallback?.isEnabled = open
    }

    /** Bumps a counter whenever the runtime queue changes (the same `RxBus.playlistObservable`
     *  the legacy queue refreshed on), so the Up Next projection re-reads the play order. */
    @Composable
    private fun PlaylistChangeTicker(onChange: () -> Unit) {
        LaunchedEffect(Unit) {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                RxBus.playlistObservable.asFlow().collect { onChange() }
            }
        }
    }

    /**
     * Projects the runtime queue into [UpNextScreen]: the shuffle-aware play order
     * (`playlistInPlayOrder` - never the raw playlist) after the current index. Read-only; every
     * action goes straight to the unchanged `MediaPlayerManager` (play / move / remove) with the
     * same play-order positions the legacy queue used.
     */
    @Composable
    private fun UpNextHost(displayState: PlayerUiState, progressFraction: () -> Float) {
        var playlistVersion by remember { mutableIntStateOf(0) }
        PlaylistChangeTicker(onChange = { playlistVersion++ })
        val tracks = remember(
            playlistVersion,
            displayState.currentIndex,
            displayState.trackId,
            displayState.isShuffleEnabled,
        ) {
            mediaPlayerManager.playlistInPlayOrder.map { it.toTrack() }
        }
        val uiState = remember(tracks, displayState) {
            UpNextUiState(
                current = displayState.takeIf { it.hasCurrentTrack }?.let {
                    UpNextCurrentUi(
                        title = it.title.orEmpty(),
                        artist = it.artist,
                        artworkModel = it.artworkModel,
                        isPlaying = it.isPlaying,
                    )
                },
                upcoming = buildUpcoming(
                    tracks.map { QueueEntry(it.id, it.title.orEmpty(), it.artist, queueMenuItems(it)) },
                    displayState.currentIndex,
                ),
            )
        }
        UpNextScreen(
            state = uiState,
            progressFraction = progressFraction,
            artworkFor = { row -> tracks.getOrNull(row.playOrderIndex)?.coverArtRequestOrNull() },
            actions = UpNextActions(
                onBack = { setUpNextOpen(false) },
                onPlay = { position -> mediaPlayerManager.play(mediaPlayerManager.getUnshuffledIndexOf(position)) },
                onMove = { from, to -> mediaPlayerManager.moveItemInPlaylist(from, to) },
                onRemove = ::removeFromQueue,
                onMenuItem = { position, item -> tracks.getOrNull(position)?.let { handleQueueMenuItem(item, it) } },
            ),
        )
    }

    /** The legacy `nowplaying_context` menu's visibility rules, unchanged. */
    private fun queueMenuItems(track: Track): List<UpNextMenuItem> = buildList {
        if (shouldUseId3Tags()) add(UpNextMenuItem.GO_TO_ARTIST)
        if (track.parent != null) add(UpNextMenuItem.GO_TO_ALBUM)
        if (!isOffline()) add(UpNextMenuItem.LYRICS)
        add(UpNextMenuItem.SHUFFLE)
        add(UpNextMenuItem.FAVORITE)
    }

    private fun handleQueueMenuItem(item: UpNextMenuItem, track: Track) {
        when (item) {
            UpNextMenuItem.GO_TO_ARTIST -> goToArtist(track)
            UpNextMenuItem.GO_TO_ALBUM -> goToAlbum(track)
            UpNextMenuItem.LYRICS -> goToLyrics(track)
            UpNextMenuItem.SHUFFLE -> toggleShuffle()
            UpNextMenuItem.FAVORITE -> toggleFavorite(track, track.starred)
        }
    }

    /** The legacy swipe-to-delete removal: play-order position -> unshuffled window index. */
    private fun removeFromQueue(playOrderPosition: Int) {
        val index = mediaPlayerManager.getUnshuffledIndexOf(playOrderPosition)
        val item = mediaPlayerManager.getMediaItemAt(index)
        toast(String.format(resources.getString(R.string.download_song_removed), item?.mediaMetadata?.title))
        mediaPlayerManager.removeFromPlaylist(index)
    }

    private fun <T : Any> Observable<T>.asFlow() = callbackFlow {
        val disposable = subscribe({ trySend(it) }, { close(it) })
        awaitClose { disposable.dispose() }
    }

    companion object {
        private const val PROGRESS_TICK_MS = 500L
        private const val SEEK_FORWARD_MS = 30_000
        private const val SEEK_BACK_MS = 8_000
    }
}
