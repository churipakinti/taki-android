/*
 * PlayerFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color.argb
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.StarRating
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.scope.ScopeFragment
import org.koin.core.component.KoinScopeComponent
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.audiofx.EqualizerController
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.shouldUseId3Tags
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.databinding.CurrentPlayingBinding
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.subsonic.NetworkAndStorageChecker
import org.moire.ultrasonic.util.CancellationToken
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.themeColor
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.launchWithToast
import org.moire.ultrasonic.util.toTrack
import org.moire.ultrasonic.view.AutoRepeatButton
import timber.log.Timber

/**
 * Contains the Music Player screen of Ultrasonic with playback controls and the playlist
 *
 */
@Suppress("LargeClass", "TooManyFunctions", "MagicNumber")
class PlayerFragment :
    ScopeFragment(),
    GestureDetector.OnGestureListener,
    KoinScopeComponent,
    CoroutineScope {

    // Backs the CoroutineScope implementation below. Fragment instances survive being
    // navigated away from and back to (e.g. Player -> Lyrics -> Player), but their view does
    // not - onDestroyView cancels this scope, so it must be replaced with a fresh, live one
    // each time onCreateView runs again, or every launch{} call after a first round trip
    // silently becomes a no-op (the scope stays permanently cancelled).
    private var mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    override val coroutineContext: CoroutineContext
        get() = mainScope.coroutineContext

    // Settings
    private var swipeDistance = 0
    private var swipeVelocity = 0
    private var jukeboxAvailable = false
    private var isEqualizerAvailable = false

    // Detectors & Callbacks
    private lateinit var gestureScanner: GestureDetector
    private lateinit var cancellationToken: CancellationToken
    private lateinit var dragTouchHelper: ItemTouchHelper

    // Data & Services
    private val networkAndStorageChecker: NetworkAndStorageChecker by inject()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private var currentSong: Track? = null
    private lateinit var viewManager: LinearLayoutManager
    private var rxBusSubscription: CompositeDisposable = CompositeDisposable()
    private lateinit var executorService: ScheduledExecutorService

    // Views and UI Elements
    private lateinit var playlistNameView: EditText
    private lateinit var heartRatingImageView: ImageView
    private lateinit var playlistFlipper: ViewFlipper
    private lateinit var emptyTextView: TextView
    private lateinit var emptyView: ConstraintLayout
    private lateinit var songTitleTextView: TextView
    private lateinit var artistTextView: TextView
    private lateinit var albumArtImageView: ImageView
    private lateinit var playlistView: RecyclerView
    private lateinit var positionTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var pauseButton: View
    private lateinit var stopButton: View
    private lateinit var playButton: View
    private lateinit var previousButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var shuffleButton: MaterialButton
    private lateinit var repeatButton: MaterialButton
    private lateinit var queueButton: View
    private lateinit var savePlaylistButton: View
    private lateinit var lyricsButton: View
    private lateinit var progressBar: SeekBar
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var queueSummaryTextView: TextView

    // While the user has a finger on the seek bar, the 500ms position-refresh loop must not
    // overwrite its progress/enabled state, or the thumb snaps back mid-drag and dragging
    // backward becomes effectively impossible.
    private var isSeekBarDragging = false

    private val hollowHeart = R.drawable.rating_heart_hollow
    private val fullHeart = R.drawable.rating_heart_full
    private lateinit var hollowHeartDrawable: Drawable
    private lateinit var fullHeartDrawable: Drawable

    private var _binding: CurrentPlayingBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val viewAdapter: BaseAdapter<Identifiable> by lazy {
        BaseAdapter(allowDuplicateEntries = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.applyTheme(this.context)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mainScope = CoroutineScope(Dispatchers.Main)
        _binding = CurrentPlayingBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    // TODO: Switch them all over to use the view binding
    private fun findViews(view: View) {
        playlistFlipper = view.findViewById(R.id.current_playing_playlist_flipper)
        emptyTextView = view.findViewById(R.id.empty_list_text)
        emptyView = view.findViewById(R.id.emptyListView)
        progressIndicator = view.findViewById(R.id.progress_indicator)
        songTitleTextView = view.findViewById(R.id.current_playing_song)
        songTitleTextView.isSelected = true
        artistTextView = view.findViewById(R.id.current_playing_artist)
        albumArtImageView = view.findViewById(R.id.current_playing_album_art_image)
        positionTextView = view.findViewById(R.id.current_playing_position)
        durationTextView = view.findViewById(R.id.current_playing_duration)
        progressBar = view.findViewById(R.id.current_playing_progress_bar)
        playlistView = view.findViewById(R.id.playlist_view)
        queueSummaryTextView = view.findViewById(R.id.queue_summary)

        pauseButton = view.findViewById(R.id.button_pause)
        stopButton = view.findViewById(R.id.button_stop)
        playButton = view.findViewById(R.id.button_start)
        nextButton = view.findViewById(R.id.button_next)
        previousButton = view.findViewById(R.id.button_previous)
        repeatButton = view.findViewById(R.id.button_repeat)
        queueButton = view.findViewById(R.id.button_queue)
        savePlaylistButton = view.findViewById(R.id.button_save_playlist)
        lyricsButton = view.findViewById(R.id.button_lyrics)
        heartRatingImageView = view.findViewById(R.id.song_rating_heart)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (this::playlistFlipper.isInitialized) {
            outState.putInt("playlistFlipper.displayedChild", playlistFlipper.displayedChild)
        }
        super.onSaveInstanceState(outState)
    }

    @Suppress("LongMethod")
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cancellationToken = CancellationToken()
        setTitle(this, R.string.button_bar_now_playing)

        val windowManager = requireActivity().windowManager
        val width: Int
        val height: Int

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            width = size.x
            height = size.y
        }

        view.findViewById<View>(R.id.player_back)?.setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<View>(R.id.player_overflow)?.setOnClickListener(::showPlayerMenu)

        // Shown while a large queue is being built in chunks (e.g. Play All on a
        // several-thousand-song album). That still takes a few seconds even after the
        // ADD_MEDIA_ITEMS_CHUNK_SIZE fix - see MediaPlayerManager.withTimelinePublishSuppressed -
        // so this tells the user something is happening instead of the screen looking frozen.
        val queueLoadingIndicator = view.findViewById<View>(R.id.current_playing_queue_loading)
        rxBusSubscription += RxBus.queueLoadingObservable.subscribe { isLoading ->
            queueLoadingIndicator?.isVisible = isLoading
        }

        swipeDistance = (width + height) * PERCENTAGE_OF_SCREEN_FOR_SWIPE / 100
        swipeVelocity = swipeDistance
        gestureScanner = GestureDetector(context, this)

        findViews(view)
        playlistFlipper.displayedChild =
            savedInstanceState?.getInt("playlistFlipper.displayedChild") ?: 0
        val previousButton: AutoRepeatButton = view.findViewById(R.id.button_previous)
        val nextButton: AutoRepeatButton = view.findViewById(R.id.button_next)
        shuffleButton = view.findViewById(R.id.button_shuffle)
        updateShuffleButtonState(mediaPlayerManager.isShufflePlayEnabled)
        updateRepeatButtonState(mediaPlayerManager.repeatMode)

        hollowHeartDrawable = ResourcesCompat.getDrawable(resources, hollowHeart, null)!!
        fullHeartDrawable = ResourcesCompat.getDrawable(resources, fullHeart, null)!!
        val secondaryActive = requireContext().themeColor(
            com.google.android.material.R.attr.colorTertiary
        )
        hollowHeartDrawable.setTint(secondaryActive)
        fullHeartDrawable.setTint(secondaryActive)

        heartRatingImageView.setOnClickListener { setSongHeartRating() }

        albumArtImageView.setOnTouchListener { _, me ->
            gestureScanner.onTouchEvent(me)
        }

        queueButton.setOnClickListener {
            toggleFullScreenAlbumArt()
        }

        savePlaylistButton.setOnClickListener {
            if (mediaPlayerManager.playlistSize > 0) {
                showSavePlaylistDialog()
            }
        }

        lyricsButton.setOnClickListener {
            navigateToLyrics(currentSong)
        }

        songTitleTextView.setOnClickListener {
            menuItemSelected(R.id.menu_show_album, currentSong)
        }

        previousButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerManager.seekToPrevious()
            }
        }

        previousButton.setOnRepeatListener {
            seek(false)
        }

        nextButton.setOnClickListener {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerManager.seekToNext()
            }
        }

        nextButton.setOnRepeatListener {
            seek(true)
        }

        pauseButton.setOnClickListener {
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerManager.pause()
            }
        }

        stopButton.setOnClickListener {
            launch(CommunicationError.getHandler(context)) {
                mediaPlayerManager.reset()
            }
        }

        playButton.setOnClickListener {
            if (!mediaPlayerManager.isJukeboxEnabled) {
                networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            }

            launch(CommunicationError.getHandler(context)) {
                mediaPlayerManager.play()
            }
        }

        shuffleButton.setOnClickListener {
            toggleShuffle()
        }

        repeatButton.setOnClickListener {
            var newRepeat = mediaPlayerManager.repeatMode + 1
            if (newRepeat == 3) {
                newRepeat = 0
            }

            mediaPlayerManager.repeatMode = newRepeat

            onPlaylistChanged()

            when (newRepeat) {
                0 -> toast(
                    R.string.download_repeat_off
                )

                1 -> toast(
                    R.string.download_repeat_single
                )

                2 -> toast(
                    R.string.download_repeat_all
                )

                else -> {
                }
            }
        }

        progressBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                launch(CommunicationError.getHandler(context)) {
                    mediaPlayerManager.seekTo(progressBar.progress)
                }
                isSeekBarDragging = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeekBarDragging = true
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        initPlaylistDisplay()

        EqualizerController.get().observe(
            requireActivity()
        ) { equalizerController ->
            isEqualizerAvailable = if (equalizerController != null) {
                Timber.d("EqualizerController Observer.onChanged received controller")
                true
            } else {
                Timber.d("EqualizerController Observer.onChanged has no controller")
                false
            }
        }

        // Observe playlist changes and update the UI
        rxBusSubscription += RxBus.playlistObservable.subscribe {
            onPlaylistChanged()
            updateSeekBar()
        }

        rxBusSubscription += RxBus.playerStateObservable.subscribe {
            update()
            updateTitle(it.state)
            updateButtonStates(it.state)
        }

        rxBusSubscription += RxBus.ratingPublishedObservable.subscribe { update ->

            // Ignore updates which are not for the current song
            if (update.id != currentSong?.id) return@subscribe
            // Ensure UI thread
            launch {
                if (update.success == false) {
                    Toast.makeText(context, "Setting rating failed", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    updateSongRatingDisplay()
                }
            }
        }

        // Query the Jukebox state in an IO Context. Uses mainScope (not a standalone
        // scope) so the check is cancelled if the view dies before it completes, instead
        // of leaking work past onDestroyView like the old dedicated ioScope did.
        mainScope.launch(Dispatchers.IO + CommunicationError.getHandler(context)) {
            try {
                jukeboxAvailable = getMusicService().isJukeboxAvailable()
            } catch (all: Exception) {
                Timber.e(all)
            }
        }

        // Subscribe to change in command availability
        mediaPlayerManager.addListener(object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                updateMediaButtonActivationState()
            }
        })

        view.setOnTouchListener { _, event -> gestureScanner.onTouchEvent(event) }
    }

    private fun updateShuffleButtonState(isEnabled: Boolean) {
        shuffleButton.alpha = ALPHA_FULL
        shuffleButton.iconTint = ColorStateList.valueOf(playerModeColor(isEnabled))
    }

    private fun updateRepeatButtonState(repeatMode: Int) {
        when (repeatMode) {
            0 -> {
                repeatButton.setIconResource(R.drawable.media_repeat_off)
                repeatButton.alpha = ALPHA_FULL
                repeatButton.iconTint = ColorStateList.valueOf(playerModeColor(false))
            }

            1 -> {
                repeatButton.setIconResource(R.drawable.media_repeat_one)
                repeatButton.alpha = ALPHA_FULL
                repeatButton.iconTint = ColorStateList.valueOf(playerModeColor(true))
            }

            2 -> {
                repeatButton.setIconResource(R.drawable.media_repeat_all)
                repeatButton.alpha = ALPHA_FULL
                repeatButton.iconTint = ColorStateList.valueOf(playerModeColor(true))
            }

            else -> {
            }
        }
    }

    private fun playerModeColor(isActive: Boolean): Int = requireContext().themeColor(
        if (isActive) {
            androidx.appcompat.R.attr.colorPrimary
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
    )

    private fun toggleShuffle() {
        val isEnabled = mediaPlayerManager.toggleShuffle()

        if (isEnabled) {
            toast(R.string.download_menu_shuffle_on)
        } else {
            toast(R.string.download_menu_shuffle_off)
        }

        updateShuffleButtonState(isEnabled)
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayerManager.currentMediaItem == null) {
            playlistFlipper.displayedChild = 1
        } else {
            // Download list and Album art must be updated when resumed
            onPlaylistChanged()
            onTrackChanged()
        }

        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable { handler.post { update(cancellationToken) } }
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService.scheduleWithFixedDelay(runnable, 0L, 500L, TimeUnit.MILLISECONDS)

        if (mediaPlayerManager.keepScreenOn) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        requireActivity().invalidateOptionsMenu()
    }

    // Scroll to current playing.
    private fun scrollToCurrent() {
        val index = mediaPlayerManager.currentMediaItemIndex

        if (index != -1) {
            viewManager.scrollToPosition(index)
        }
    }

    override fun onPause() {
        super.onPause()
        executorService.shutdown()
    }

    override fun onDestroyView() {
        rxBusSubscription.dispose()
        cancel("CoroutineScope cancelled because the view was destroyed")
        cancellationToken.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun showPlayerMenu(anchor: View) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menuInflater.inflate(R.menu.nowplaying, popup.menu)
        setupOptionsMenu(popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            menuItemSelected(menuItem.itemId, currentSong)
        }
        popup.show()
    }

    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    fun setupOptionsMenu(menu: Menu) {
        // Seems there is nothing like ViewBinding for Menus
        val screenOption = menu.findItem(R.id.menu_item_screen_on_off)
        val goToAlbum = menu.findItem(R.id.menu_show_album)
        val goToArtist = menu.findItem(R.id.menu_show_artist)
        val jukeboxOption = menu.findItem(R.id.menu_item_jukebox)
        val equalizerMenuItem = menu.findItem(R.id.menu_item_equalizer)
        if (equalizerMenuItem != null) {
            equalizerMenuItem.isEnabled = isEqualizerAvailable
            equalizerMenuItem.isVisible = isEqualizerAvailable
        }

        val track = mediaPlayerManager.currentMediaItem?.toTrack()

        if (track != null) {
            currentSong = track
        }

        if (currentSong != null) {
            goToAlbum.isVisible = true
            goToArtist.isVisible = true
        } else {
            goToAlbum.isVisible = false
            goToArtist.isVisible = false
        }

        if (mediaPlayerManager.keepScreenOn) {
            screenOption?.setTitle(R.string.download_menu_screen_off)
        } else {
            screenOption?.setTitle(R.string.download_menu_screen_on)
        }

        if (jukeboxOption != null) {
            jukeboxOption.isEnabled = jukeboxAvailable
            jukeboxOption.isVisible = jukeboxAvailable
            if (mediaPlayerManager.isJukeboxEnabled) {
                jukeboxOption.setTitle(R.string.download_menu_jukebox_off)
            } else {
                jukeboxOption.setTitle(R.string.download_menu_jukebox_on)
            }
        }
    }

    private fun onCreateContextMenu(view: View, track: Track): PopupMenu {
        val popup = PopupMenu(view.context, view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.nowplaying_context, popup.menu)

        if (track.parent == null) {
            val menuItem = popup.menu.findItem(R.id.menu_show_album)
            if (menuItem != null) {
                menuItem.isVisible = false
            }
        }

        // Only show the menu if the ID3 tags are available
        popup.menu.findItem(R.id.menu_show_artist)?.isVisible = shouldUseId3Tags()

        // Only show the lyrics when the user is online
        popup.menu.findItem(R.id.menu_lyrics)?.isVisible = !isOffline()
        popup.show()
        return popup
    }

    @Suppress("MagicNumber")
    private fun showRatingPopup(track: Track) {
        val popup = PopupMenu(requireContext(), playlistView)
        popup.menuInflater.inflate(R.menu.rating, popup.menu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popup.setForceShowIcon(true)

        popup.setOnMenuItemClickListener {
            val rating = when (it.itemId) {
                R.id.popup_rate_1 -> 1
                R.id.popup_rate_2 -> 2
                R.id.popup_rate_3 -> 3
                R.id.popup_rate_4 -> 4
                R.id.popup_rate_5 -> 5
                else -> 0
            }
            track.userRating = rating
            RxBus.ratingSubmitter.onNext(RatingUpdate(track.id, StarRating(5, rating.toFloat())))
            true
        }
        popup.show()
    }

    private fun onContextMenuItemSelected(menuItem: MenuItem, item: MusicDirectory.Child): Boolean {
        if (item !is Track) return false
        return menuItemSelected(menuItem.itemId, item)
    }

    @Suppress("ComplexMethod", "LongMethod", "ReturnCount")
    private fun menuItemSelected(menuItemId: Int, track: Track?): Boolean {
        when (menuItemId) {
            R.id.menu_show_artist -> {
                if (track == null) return false

                if (Settings.id3TagsEnabledOnline) {
                    val action = PlayerFragmentDirections.playerToAlbumsList(
                        type = AlbumListType.SORTED_BY_NAME,
                        byArtist = true,
                        id = track.artistId,
                        title = track.artist,
                        offset = 0,
                        size = 1000
                    )
                    findNavController().navigate(action)
                }
                return true
            }

            R.id.menu_show_album -> {
                if (track == null) return false

                val albumId = if (shouldUseId3Tags()) track.albumId else track.parent

                val action = PlayerFragmentDirections.playerToSelectAlbum(
                    id = albumId,
                    name = track.album,
                    parentId = track.parent,
                    isAlbum = true
                )
                findNavController().navigate(action)
                return true
            }

            R.id.menu_lyrics -> {
                navigateToLyrics(track)
                return true
            }

            R.id.menu_item_screen_on_off -> {
                val window = requireActivity().window
                if (mediaPlayerManager.keepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mediaPlayerManager.keepScreenOn = false
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    mediaPlayerManager.keepScreenOn = true
                }
                return true
            }

            R.id.menu_shuffle -> {
                toggleShuffle()
                return true
            }

            R.id.song_menu_favorite -> {
                if (track == null) return false
                track.starred = !track.starred
                RxBus.ratingSubmitter.onNext(RatingUpdate(track.id, HeartRating(track.starred)))
                return true
            }

            R.id.song_menu_rate -> {
                if (track == null) return false
                showRatingPopup(track)
                return true
            }

            R.id.menu_item_equalizer -> {
                Navigation.findNavController(requireView()).navigate(R.id.playerToEqualizer)
                return true
            }

            R.id.menu_item_jukebox -> {
                val jukeboxEnabled = !mediaPlayerManager.isJukeboxEnabled
                mediaPlayerManager.isJukeboxEnabled = jukeboxEnabled
                toast(
                    if (jukeboxEnabled) {
                        R.string.download_jukebox_on
                    } else {
                        R.string.download_jukebox_off
                    },
                    false
                )
                return true
            }

            R.id.menu_item_clear_playlist -> {
                mediaPlayerManager.isShufflePlayEnabled = false
                mediaPlayerManager.clear()
                onPlaylistChanged()
                return true
            }

            R.id.menu_item_save_playlist -> {
                if (mediaPlayerManager.playlistSize > 0) {
                    showSavePlaylistDialog()
                }
                return true
            }

            else -> return false
        }
    }

    private fun navigateToLyrics(track: Track?) {
        if (isOffline()) {
            toast(R.string.download_lyrics_offline)
            return
        }
        if (track?.artist == null || track.title == null) return
        val action = PlayerFragmentDirections.playerToLyrics(
            track.artist!!,
            track.title!!,
            track.id
        )
        Navigation.findNavController(requireView()).navigate(action)
    }

    private fun update(cancel: CancellationToken? = null) {
        if (cancel?.isCancellationRequested == true) return
        if (currentSong?.id != mediaPlayerManager.currentMediaItem?.mediaId) {
            onTrackChanged()
        }
        updateSeekBar()
    }

    private fun savePlaylistInBackground(playlistName: String) {
        toast(resources.getString(R.string.download_playlist_saving, playlistName))
        mediaPlayerManager.suggestedPlaylistName = playlistName

        // The playlist can be acquired only from the main thread
        val entries = mediaPlayerManager.playlist.map {
            it.toTrack()
        }

        // launchWithToast is bound to the Fragment's (activity) lifecycle rather than the
        // view, so it stays safe to complete after onDestroyView instead of toasting on a
        // detached Fragment like the previous ioScope-backed version could.
        launchWithToast {
            try {
                withContext(Dispatchers.IO) {
                    getMusicService().createPlaylist(null, playlistName, entries)
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
                    CommunicationError.getErrorMessage(all)
                )
            }
        }
    }

    private fun toggleFullScreenAlbumArt() {
        if (playlistFlipper.displayedChild == 1) {
            playlistFlipper.inAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_down_in)
            playlistFlipper.outAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_down_out)
            playlistFlipper.displayedChild = 0
        } else {
            playlistFlipper.inAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_up_in)
            playlistFlipper.outAnimation =
                AnimationUtils.loadAnimation(context, R.anim.push_up_out)
            playlistFlipper.displayedChild = 1
        }
        scrollToCurrent()
    }

    private fun initPlaylistDisplay() {
        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        playlistView.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        // Create listener
        val clickHandler: ((Track, Int) -> Unit) = { _, listPos ->
            val mediaIndex = mediaPlayerManager.getUnshuffledIndexOf(listPos)
            mediaPlayerManager.play(mediaIndex)
        }

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = clickHandler,
                onContextMenuClick = { menu, id -> onContextMenuItemSelected(menu, id) },
                checkable = false,
                draggable = true,
                lifecycleOwner = viewLifecycleOwner,
                // The row's star rating icon was replaced with "Favorite"/"Rate" actions in the
                // long-press context menu (see nowplaying_context.xml) -- less visual noise per
                // row, same underlying rating/starred fields.
                showRating = false,
                queueStyle = true,
                layout = R.layout.list_item_queue_track
            ) { view, track, _ -> onCreateContextMenu(view, track) }.apply {
                this.startDrag = { holder ->
                    dragTouchHelper.startDrag(holder)
                }
            }
        )

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {

            var dragging = false
            var startPosition = 0
            var endPosition = 0

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition

                // The item must be moved manually in the viewAdapter, because it must be
                // moved synchronously, before this function returns. AsyncListDiffer would execute
                // the move too late
                val items = viewAdapter.getCurrentList().toMutableList()
                if (from < to) {
                    for (i in from until to) {
                        Collections.swap(items, i, i + 1)
                    }
                } else {
                    for (i in from downTo to + 1) {
                        Collections.swap(items, i, i - 1)
                    }
                }
                viewAdapter.setList(items)
                viewAdapter.notifyItemMoved(from, to)
                endPosition = to

                // When the user moves an item, onMove may be called many times quickly,
                // especially while scrolling. We only update the playlist when the item
                // is released (see onSelectedChanged)

                // It was moved, so return true
                return true
            }

            // Swipe to delete from playlist
            @SuppressLint("NotifyDataSetChanged")
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val viewPos = viewHolder.bindingAdapterPosition
                val pos = mediaPlayerManager.getUnshuffledIndexOf(viewPos)
                val item = mediaPlayerManager.getMediaItemAt(pos)

                // Remove the item from the list quickly
                val items = viewAdapter.getCurrentList().toMutableList()
                items.removeAt(pos)
                viewAdapter.setList(items)
                viewAdapter.notifyItemRemoved(pos)

                val songRemoved = String.format(
                    resources.getString(R.string.download_song_removed),
                    item?.mediaMetadata?.title
                )

                toast(songRemoved)

                // Remove the item from the playlist
                mediaPlayerManager.removeFromPlaylist(pos)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = ALPHA_DEACTIVATED
                    dragging = true
                    startPosition = viewHolder!!.bindingAdapterPosition
                }

                // We only move the item in the playlist when the user finished dragging
                if (actionState == ACTION_STATE_IDLE && dragging) {
                    dragging = false
                    // Move the item in the playlist separately
                    Timber.i("Moving item %s to %s", startPosition, endPosition)
                    mediaPlayerManager.moveItemInPlaylist(startPosition, endPosition)
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val drawable = ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_menu_remove_all,
                        null
                    )
                    val iconSize = Util.dpToPx(ICON_SIZE, activity!!)
                    val swipeRatio = abs(dX) / viewHolder.itemView.width.toFloat()
                    val itemAlpha = ALPHA_FULL - swipeRatio
                    val backgroundAlpha = min(ALPHA_HALF + swipeRatio, ALPHA_FULL)
                    val backgroundColor = argb((backgroundAlpha * 255).toInt(), 255, 0, 0)

                    if (dX > 0) {
                        canvas.clipRect(
                            itemView.left.toFloat(),
                            itemView.top.toFloat(),
                            dX,
                            itemView.bottom.toFloat()
                        )
                        canvas.drawColor(backgroundColor)
                        val left = itemView.left + Util.dpToPx(16, activity!!)
                        val top = itemView.top + (itemView.bottom - itemView.top - iconSize) / 2
                        drawable?.setBounds(left, top, left + iconSize, top + iconSize)
                        drawable?.draw(canvas)
                    } else {
                        canvas.clipRect(
                            itemView.right.toFloat() + dX,
                            itemView.top.toFloat(),
                            itemView.right.toFloat(),
                            itemView.bottom.toFloat()
                        )
                        canvas.drawColor(backgroundColor)
                        val left = itemView.right - Util.dpToPx(16, activity!!) - iconSize
                        val top = itemView.top + (itemView.bottom - itemView.top - iconSize) / 2
                        drawable?.setBounds(left, top, left + iconSize, top + iconSize)
                        drawable?.draw(canvas)
                    }

                    // Fade out the view as it is swiped out of the parent's bounds
                    viewHolder.itemView.alpha = itemAlpha
                    viewHolder.itemView.translationX = dX
                } else {
                    super.onChildDraw(
                        canvas,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            }
        }

        dragTouchHelper = ItemTouchHelper(callback)

        dragTouchHelper.attachToRecyclerView(playlistView)
    }

    private fun onPlaylistChanged() {
        // Try to display playlist in play order
        val list = mediaPlayerManager.playlistInPlayOrder
        emptyTextView.setText(R.string.playlist_empty)
        viewAdapter.submitList(list.map(MediaItem::toTrack))
        progressIndicator.isVisible = false
        emptyView.isVisible = list.isEmpty()
        queueSummaryTextView.text = resources.getQuantityString(
            R.plurals.n_songs,
            list.size,
            list.size
        )

        updateRepeatButtonState(mediaPlayerManager.repeatMode)
    }

    private fun onTrackChanged() {
        currentSong = mediaPlayerManager.currentMediaItem?.toTrack()

        scrollToCurrent()
        if (currentSong != null) {
            songTitleTextView.text = currentSong!!.title
            artistTextView.text = currentSong!!.artist

            imageLoaderProvider.executeOn {
                it.loadImage(albumArtImageView, currentSong, true, 0)
            }

            updateSongRatingDisplay()
        } else {
            currentSong = null
            songTitleTextView.text = null
            artistTextView.text = null
            imageLoaderProvider.executeOn {
                it.loadImage(albumArtImageView, null, true, 0)
            }
        }

        updateSongRatingDisplay()

        updateMediaButtonActivationState()
    }

    private fun updateMediaButtonActivationState() {
        nextButton.isEnabled = mediaPlayerManager.canSeekToNext()
        previousButton.isEnabled = mediaPlayerManager.canSeekToPrevious()
    }

    @Synchronized
    private fun updateSeekBar() {
        val isJukeboxEnabled: Boolean = mediaPlayerManager.isJukeboxEnabled
        val millisPlayed: Int = max(0, mediaPlayerManager.playerPosition)
        val duration: Int = mediaPlayerManager.playerDuration
        val playbackState: Int = mediaPlayerManager.playbackState

        if (currentSong != null) {
            durationTextView.text = Util.formatTotalDuration(duration.toLong(), true)
            progressBar.max = if (duration == 0) 100 else duration // Work-around for apparent bug.
            // Don't fight the user's finger: while dragging, the thumb/position must only
            // reflect the drag gesture, not the real playback position.
            if (!isSeekBarDragging) {
                positionTextView.text = Util.formatTotalDuration(millisPlayed.toLong(), true)
                progressBar.progress = millisPlayed
                progressBar.isEnabled = mediaPlayerManager.isPlaying || isJukeboxEnabled
            }
        } else {
            positionTextView.setText(R.string.util_zero_time)
            durationTextView.setText(R.string.util_no_time)
            progressBar.progress = 0
            progressBar.max = 0
            progressBar.isEnabled = false
        }

        val progress = mediaPlayerManager.bufferedPercentage
        updateBufferProgress(playbackState, progress)
    }

    private fun updateTitle(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                val downloadStatus = resources.getString(
                    R.string.download_playerstate_loading
                )
                setTitle(this@PlayerFragment, downloadStatus)
            }

            Player.STATE_READY -> {
                if (mediaPlayerManager.isShufflePlayEnabled) {
                    setTitle(
                        this@PlayerFragment,
                        R.string.download_playerstate_playing_shuffle
                    )
                } else {
                    setTitle(this@PlayerFragment, R.string.button_bar_now_playing)
                }
            }

            Player.STATE_IDLE, Player.STATE_ENDED -> {}

            else -> setTitle(this@PlayerFragment, R.string.button_bar_now_playing)
        }
    }

    private fun updateBufferProgress(playbackState: Int, progress: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY -> {
                progressBar.secondaryProgress = progress
            }

            else -> { }
        }
    }

    private fun updateButtonStates(playbackState: Int) {
        val isPlaying = mediaPlayerManager.isPlaying
        when (playbackState) {
            Player.STATE_READY -> {
                pauseButton.isVisible = isPlaying
                stopButton.isVisible = false
                playButton.isVisible = !isPlaying
            }

            Player.STATE_BUFFERING -> {
                pauseButton.isVisible = false
                stopButton.isVisible = true
                playButton.isVisible = false
            }

            else -> {
                pauseButton.isVisible = false
                stopButton.isVisible = false
                playButton.isVisible = true
            }
        }
    }

    private fun seek(forward: Boolean) {
        launch(CommunicationError.getHandler(context)) {
            if (forward) {
                mediaPlayerManager.seekForward()
            } else {
                mediaPlayerManager.seekBack()
            }
        }
    }

    override fun onDown(me: MotionEvent): Boolean = false

    @Suppress("ReturnCount")
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val e1X = e1?.x ?: 0F
        val e2X = e2.x
        val e1Y = e1?.y ?: 0F
        val e2Y = e2.y
        val absX = abs(velocityX)
        val absY = abs(velocityY)

        // Right to Left swipe
        if (e1X - e2X > swipeDistance && absX > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerManager.seekToNext()
            return true
        }

        // Left to Right swipe
        if (e2X - e1X > swipeDistance && absX > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerManager.seekToPrevious()
            return true
        }

        // Top to Bottom swipe
        if (e2Y - e1Y > swipeDistance && absY > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerManager.seekTo(mediaPlayerManager.playerPosition + 30000)
            return true
        }

        // Bottom to Top swipe
        if (e1Y - e2Y > swipeDistance && absY > swipeVelocity) {
            networkAndStorageChecker.warnIfNetworkOrStorageUnavailable()
            mediaPlayerManager.seekTo(mediaPlayerManager.playerPosition - 8000)
            return true
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean = false

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    private fun updateSongRatingDisplay() {
        val isHeartSet = currentSong?.starred ?: false

        if (isHeartSet) {
            heartRatingImageView.setImageDrawable(fullHeartDrawable)
        } else {
            heartRatingImageView.setImageDrawable(hollowHeartDrawable)
        }
    }

    private fun setSongHeartRating() {
        if (currentSong == null) return
        currentSong?.starred = !(currentSong?.starred ?: true)
        updateSongRatingDisplay()

        RxBus.ratingSubmitter.onNext(
            RatingUpdate(
                currentSong!!.id,
                HeartRating(currentSong?.starred ?: false)
            )
        )
    }

    @SuppressLint("InflateParams")
    private fun showSavePlaylistDialog() {
        val layout = LayoutInflater.from(this.context)
            .inflate(R.layout.save_playlist, null)

        playlistNameView = layout.findViewById(R.id.save_playlist_name)

        val builder = ConfirmationDialog.Builder(requireContext())
        builder.setTitle(R.string.download_playlist_title)
        builder.setMessage(R.string.download_playlist_name)

        builder.setPositiveButton(R.string.common_save) { _, _ ->
            savePlaylistInBackground(
                playlistNameView.text.toString()
            )
        }

        builder.setNegativeButton(R.string.common_cancel) { dialog, _ -> dialog.cancel() }
        builder.setView(layout)
        builder.setCancelable(true)
        val dialog = builder.create()
        val playlistName = mediaPlayerManager.suggestedPlaylistName
        if (playlistName != null) {
            playlistNameView.setText(playlistName)
        } else {
            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            playlistNameView.setText(dateFormat.format(Date()))
        }
        dialog.show()
    }

    companion object {
        private const val PERCENTAGE_OF_SCREEN_FOR_SWIPE = 5
        private const val ALPHA_FULL = 1f
        private const val ALPHA_HALF = 0.5f
        private const val ALPHA_DEACTIVATED = 0.4f
        private const val ICON_SIZE = 32
    }
}
