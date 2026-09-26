/*
 * PlayerFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.HeartRating
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Collections
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
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.shouldUseId3Tags
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.SleepTimerState
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.ui.player.NowPlayingActions
import org.moire.ultrasonic.ui.player.NowPlayingOverflowItem
import org.moire.ultrasonic.ui.player.NowPlayingScreen
import org.moire.ultrasonic.ui.player.UpNextItem
import org.moire.ultrasonic.ui.playback.PlaybackProgress
import org.moire.ultrasonic.ui.playback.PlaybackUiStateHolder
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
 * dialogs, never the other way. The one deliberately-not-Compose piece is the queue (the legacy
 * `current_playlist.xml`: drag-reorder, swipe-to-delete, tap-to-play, its own long-press context
 * menu) - embedded unchanged via `AndroidView`, toggled in place of the hero artwork exactly
 * like the legacy `ViewFlipper` did (see `showQueue` in [onCreateView]).
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
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    val state by playbackUiStateHolder.playerState.collectAsStateWithLifecycle()
                    val sleepTimerState by playbackUiStateHolder.sleepTimerState
                        .collectAsStateWithLifecycle()
                    var progress by remember { mutableStateOf(PlaybackProgress()) }
                    var showQueue by rememberSaveable { mutableStateOf(false) }

                    ProgressTicker(isPlaying = state.isPlaying, onTick = { progress = it })

                    val displayState = favoriteOverride?.let { (trackId, liked) ->
                        if (trackId == state.trackId) state.copy(isCurrentTrackLiked = liked) else null
                    } ?: state

                    var playlistVersion by remember { mutableIntStateOf(0) }
                    PlaylistChangeTicker(onChange = { playlistVersion++ })
                    val upNext = remember(displayState.currentIndex, displayState.trackId, playlistVersion) {
                        computeUpNext(displayState.currentIndex)
                    }

                    NowPlayingScreen(
                        state = displayState,
                        progress = progress,
                        sleepTimerState = sleepTimerState,
                        showQueue = showQueue,
                        actions = nowPlayingActions(
                            onToggleQueue = { showQueue = !showQueue },
                            currentlyLiked = displayState.isCurrentTrackLiked,
                        ),
                        queueContent = { QueueView() },
                        upNext = upNext,
                    )
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

    /** Bumps a counter whenever the queue changes, so the Up Next preview re-reads the play order
     *  (the same `RxBus.playlistObservable` the queue view refreshes on). */
    @Composable
    private fun PlaylistChangeTicker(onChange: () -> Unit) {
        LaunchedEffect(Unit) {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                RxBus.playlistObservable.asFlow().collect { onChange() }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

    private fun nowPlayingActions(onToggleQueue: () -> Unit, currentlyLiked: Boolean): NowPlayingActions {
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
            onToggleQueue = onToggleQueue,
            onSleepTimer = ::showSleepTimerDialog,
            onOverflowItem = { item -> handleOverflow(item, currentTrack()) },
            equalizerAvailable = { isEqualizerAvailable },
            keepScreenOnActive = { mediaPlayerManager.keepScreenOn },
            onArtworkSwipeNext = { fling(playbackUiStateHolder::onNext) },
            onArtworkSwipePrevious = { fling(playbackUiStateHolder::onPrevious) },
            onArtworkSwipeSeekForward = { flingSeek(SEEK_FORWARD_MS) },
            onArtworkSwipeSeekBack = { flingSeek(-SEEK_BACK_MS) },
            onPlayUpNext = { playOrderIndex ->
                mediaPlayerManager.play(mediaPlayerManager.getUnshuffledIndexOf(playOrderIndex))
            },
        )
    }

    /** The Up Next preview (issue #10 phase 4J3): the next [UP_NEXT_PREVIEW_COUNT] items after the
     *  current one in the shuffle-aware play order - a read-only projection of the same list the
     *  queue view renders. */
    private fun computeUpNext(currentIndex: Int): List<UpNextItem> {
        if (currentIndex < 0) return emptyList()
        val start = currentIndex + 1
        return mediaPlayerManager.playlistInPlayOrder.drop(start).take(UP_NEXT_PREVIEW_COUNT)
            .mapIndexed { offset, item ->
                val track = item.toTrack()
                UpNextItem(
                    title = track.title.orEmpty(),
                    artist = track.artist,
                    artworkModel = track.coverArtRequestOrNull(),
                    playOrderIndex = start + offset,
                )
            }
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
     * Sleep timer picker: a plain single-choice `MaterialAlertDialog`, unchanged - reusing the
     * legacy flow verbatim rather than building a Compose dialog (issue #10 phase 4J scope).
     */
    private fun showSleepTimerDialog() {
        val hasCurrentSong = mediaPlayerManager.currentMediaItem != null
        val labels = mutableListOf(getString(R.string.sleep_timer_off))
        for (minutes in SLEEP_TIMER_DURATIONS_MINUTES) {
            labels += resources.getQuantityString(R.plurals.sleep_timer_option_minutes, minutes, minutes)
        }
        if (hasCurrentSong) labels += getString(R.string.sleep_timer_end_of_song)

        val timerState = mediaPlayerManager.sleepTimerState
        val checkedIndex = when (timerState) {
            SleepTimerState.Off -> 0
            is SleepTimerState.EndOfTrack -> if (hasCurrentSong) labels.lastIndex else -1
            is SleepTimerState.Duration -> {
                val presetIndex = SLEEP_TIMER_DURATIONS_MINUTES.indexOf(timerState.presetMinutes)
                if (presetIndex >= 0) presetIndex + 1 else -1
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sleep_timer_title)
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                dialog.dismiss()
                onSleepTimerOptionSelected(which, hasCurrentSong)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun onSleepTimerOptionSelected(which: Int, hasCurrentSong: Boolean) {
        when {
            which == 0 -> {
                playbackUiStateHolder.onCancelSleepTimer()
                toast(R.string.sleep_timer_confirm_cancelled)
            }

            which <= SLEEP_TIMER_DURATIONS_MINUTES.size -> {
                val minutes = SLEEP_TIMER_DURATIONS_MINUTES[which - 1]
                playbackUiStateHolder.onSetSleepTimer(minutes)
                toast(resources.getQuantityString(R.plurals.sleep_timer_confirm_minutes, minutes, minutes))
            }

            hasCurrentSong -> {
                playbackUiStateHolder.onSetSleepTimerEndOfTrack()
                toast(R.string.sleep_timer_confirm_end_of_song)
            }
        }
    }

    // --- The legacy queue view (issue #10 phase 4J: deliberately not reimplemented in Compose)

    /**
     * Hosts the unchanged `current_playlist.xml` (drag-reorder, swipe-to-delete, tap-to-play,
     * its own long-press context menu) via `AndroidView`. A fresh `RecyclerView` +
     * `ItemTouchHelper` per composition of this slot (the `factory` runs once per `AndroidView`
     * insertion into the tree, not per recomposition) - Compose does not touch it afterwards,
     * it manages its own RxBus subscription against `viewLifecycleOwner`.
     */
    @Composable
    private fun QueueView() {
        AndroidView(factory = { ctx -> inflateQueueView(ctx) }, modifier = Modifier.fillMaxSize())
    }

    @Suppress("LongMethod")
    @SuppressLint("InflateParams")
    private fun inflateQueueView(context: Context): View {
        // No real parent exists yet - this becomes the AndroidView's own root (see [QueueView]),
        // the standard pattern for an AndroidView factory. Matches the legacy showSavePlaylistDialog's
        // own suppressed inflate() above.
        val root = LayoutInflater.from(context).inflate(R.layout.current_playlist, null, false)
        val emptyView = root.findViewById<ConstraintLayout>(R.id.emptyListView)
        val emptyTextView = root.findViewById<TextView>(R.id.empty_list_text)
        val progressIndicator = root.findViewById<CircularProgressIndicator>(R.id.progress_indicator)
        val queueSummaryTextView = root.findViewById<TextView>(R.id.queue_summary)
        val playlistView = root.findViewById<FastScrollRecyclerView>(R.id.playlist_view)
        emptyTextView.setText(R.string.playlist_empty)

        val viewAdapter = BaseAdapter<Identifiable>(allowDuplicateEntries = true)
        val clickHandler: (Track, Int) -> Unit = { _, listPos ->
            val mediaIndex = mediaPlayerManager.getUnshuffledIndexOf(listPos)
            mediaPlayerManager.play(mediaIndex)
        }
        lateinit var dragTouchHelper: ItemTouchHelper
        viewAdapter.register(
            TrackViewBinder(
                onItemClick = clickHandler,
                onContextMenuClick = { menu, track -> onQueueContextMenuItemSelected(menu, track) },
                checkable = false,
                draggable = true,
                lifecycleOwner = viewLifecycleOwner,
                showRating = false,
                queueStyle = true,
                layout = R.layout.list_item_queue_track,
            ) { view, track, _ -> onCreateQueueContextMenu(view, track) }.apply {
                startDrag = { holder -> dragTouchHelper.startDrag(holder) }
            },
        )

        playlistView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            adapter = viewAdapter
        }
        dragTouchHelper = ItemTouchHelper(QueueDragCallback(viewAdapter))
        dragTouchHelper.attachToRecyclerView(playlistView)

        fun refresh() {
            val list = mediaPlayerManager.playlistInPlayOrder
            viewAdapter.submitList(list.map { it.toTrack() })
            progressIndicator.isVisible = false
            emptyView.isVisible = list.isEmpty()
            queueSummaryTextView.text = resources.getQuantityString(R.plurals.n_songs, list.size, list.size)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                RxBus.playlistObservable.asFlow().collect { refresh() }
            }
        }
        return root
    }

    private fun <T : Any> Observable<T>.asFlow() = callbackFlow {
        val disposable = subscribe({ trySend(it) }, { close(it) })
        awaitClose { disposable.dispose() }
    }

    private fun onCreateQueueContextMenu(view: View, track: Track): PopupMenu {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.nowplaying_context, popup.menu)

        if (track.parent == null) {
            popup.menu.findItem(R.id.menu_show_album)?.isVisible = false
        }
        popup.menu.findItem(R.id.menu_show_artist)?.isVisible = shouldUseId3Tags()
        popup.menu.findItem(R.id.menu_lyrics)?.isVisible = !isOffline()
        popup.show()
        return popup
    }

    private fun onQueueContextMenuItemSelected(menuItem: MenuItem, item: Track): Boolean {
        when (menuItem.itemId) {
            R.id.menu_show_artist -> goToArtist(item)
            R.id.menu_show_album -> goToAlbum(item)
            R.id.menu_lyrics -> goToLyrics(item)
            R.id.menu_shuffle -> toggleShuffle()
            R.id.song_menu_favorite -> toggleFavorite(item, item.starred)
            else -> return false
        }
        return true
    }

    /**
     * The legacy `ItemTouchHelper.SimpleCallback`: reorder (drag) commits to the playlist only
     * on release, and swipe removes the row - both unchanged, including the documented
     * shuffle-position limitation on reorder (see the phase 4J report). The legacy custom
     * red/trash-icon swipe-reveal `onChildDraw` canvas drawing is deliberately not ported (a
     * cosmetic-only simplification - the default Material swipe animation applies instead);
     * the swipe-to-delete function itself is unchanged.
     */
    private inner class QueueDragCallback(
        private val viewAdapter: BaseAdapter<Identifiable>,
    ) : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    ) {
        private var dragging = false
        private var startPosition = 0
        private var endPosition = 0

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            val items = viewAdapter.getCurrentList().toMutableList()
            if (from < to) {
                for (i in from until to) Collections.swap(items, i, i + 1)
            } else {
                for (i in from downTo to + 1) Collections.swap(items, i, i - 1)
            }
            viewAdapter.setList(items)
            viewAdapter.notifyItemMoved(from, to)
            endPosition = to
            return true
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val viewPos = viewHolder.bindingAdapterPosition
            val pos = mediaPlayerManager.getUnshuffledIndexOf(viewPos)
            val item = mediaPlayerManager.getMediaItemAt(pos)

            val items = viewAdapter.getCurrentList().toMutableList()
            items.removeAt(pos)
            viewAdapter.setList(items)
            viewAdapter.notifyItemRemoved(pos)

            toast(String.format(resources.getString(R.string.download_song_removed), item?.mediaMetadata?.title))
            mediaPlayerManager.removeFromPlaylist(pos)
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.alpha = ALPHA_DEACTIVATED
                dragging = true
                startPosition = viewHolder!!.bindingAdapterPosition
            }
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && dragging) {
                dragging = false
                mediaPlayerManager.moveItemInPlaylist(startPosition, endPosition)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = ALPHA_FULL
        }

        override fun isLongPressDragEnabled(): Boolean = false
    }

    companion object {
        private const val PROGRESS_TICK_MS = 500L
        private const val UP_NEXT_PREVIEW_COUNT = 1
        private const val SEEK_FORWARD_MS = 30_000
        private const val SEEK_BACK_MS = 8_000
        private const val ALPHA_FULL = 1f
        private const val ALPHA_DEACTIVATED = 0.4f
        private val SLEEP_TIMER_DURATIONS_MINUTES = listOf(15, 30, 45, 60)
    }
}
