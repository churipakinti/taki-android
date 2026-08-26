/*
 * TrackCollectionFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.HeartRating
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Collections
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumDetailHeaderBinder
import org.moire.ultrasonic.adapters.AlbumHeader
import org.moire.ultrasonic.adapters.AlbumRowDelegate
import org.moire.ultrasonic.adapters.DiscHeader
import org.moire.ultrasonic.adapters.DiscHeaderBinder
import org.moire.ultrasonic.adapters.HeaderViewBinder
import org.moire.ultrasonic.adapters.LibraryTrackBinder
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.adapters.Utils
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ActiveServerProvider.Companion.isOffline
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.TrackCollectionModel
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.service.RxBus
import org.moire.ultrasonic.service.plusAssign
import org.moire.ultrasonic.subsonic.VideoPlayer
import org.moire.ultrasonic.util.ConfirmationDialog
import org.moire.ultrasonic.util.ContextMenuUtil
import org.moire.ultrasonic.util.DownloadAction
import org.moire.ultrasonic.util.DownloadUtil
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.FileUtil
import org.moire.ultrasonic.util.PerfMetrics
import org.moire.ultrasonic.util.PlaylistUtil
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.FilterButtonBar
import org.moire.ultrasonic.view.FilterPrimaryAction
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities
import timber.log.Timber

/**
 * Displays a group of tracks, eg. the songs of an album, of a playlist etc.
 *
 * In most cases the data should be just a list of Entries, but there are some cases
 * where the list can contain Albums as well. This happens especially when having ID3 tags disabled,
 * or using Offline mode, both in which Indexes instead of Artists are being used.
 */
@Suppress("TooManyFunctions")
open class TrackCollectionFragment(initialOrder: SortOrder? = null) :
    MultiListFragment<MusicDirectory.Child>(),
    FilterableFragment {

    private var albumButtons: View? = null
    private var downloadButton: MaterialButton? = null
    private var addPlaylistButton: MaterialButton? = null
    private var playAllButtonVisible = false
    private var playAllButton: MenuItem? = null

    internal val mediaPlayerManager: MediaPlayerManager by inject()

    override val listModel: TrackCollectionModel by viewModels()
    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

    private var sortOrder = initialOrder
    private var selectedArtistId: String? = null
    private var selectedArtistName: String? = null
    private var selectedGenreName: String? = null
    private var pendingFilterSelection: SortOrder? = null
    private var availableArtists: List<ArtistOrIndex> = emptyList()
    private var filterButtonBar: FilterButtonBar? = null
    private var loadJob: Job? = null
    private var albumHasMultipleArtists = false
    private var albumHasMultipleDiscs = false
    private var albumNotes: String? = null
    private var selectionModeActive = false
    private var pendingAddToPlaylistTracks: List<Track>? = null
    private var availablePlaylists: List<Playlist> = emptyList()
    private var pendingPlaylistHeaderMenu = false

    /**
     * The id of the main layout
     */
    override val mainLayout: Int = R.layout.list_layout_track

    // Don't force a network refetch every time this screen opens (e.g. reopening the same
    // album) -- only the album/folder/videos paths are actually cached (see CachedMusicService),
    // but a refresh=true default here bypassed that cache unconditionally on every navigation.
    // This mirrors the fix already applied to AlbumListFragment (commit 026aa795, "don't refresh
    // the album list on back navigation") that was never extended to this screen.
    override val refreshOnCreation: Boolean = false

    private val navArgs: TrackCollectionFragmentArgs by navArgs()

    private val isMediaLibrarySongs: Boolean
        get() = parentFragment is MainFragment || navArgs.libraryRoot || navArgs.getStarred

    private val useLibraryTrackRows: Boolean
        get() = isMediaLibrarySongs || navArgs.genreName != null || navArgs.dailyMix

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (sortOrder == null && navArgs.libraryRoot) {
            sortOrder = SortOrder.ALL_SONGS
        }
        val sortOrderName = savedInstanceState?.getString("sort_order")
        sortOrderName?.let { sortOrder = SortOrder.valueOf(it) }
        selectedArtistId = savedInstanceState?.getString(SELECTED_ARTIST_ID_KEY)
        selectedArtistName = savedInstanceState?.getString(SELECTED_ARTIST_NAME_KEY)
        selectedGenreName = savedInstanceState?.getString(SELECTED_GENRE_KEY)
        pendingFilterSelection = savedInstanceState?.getString(PENDING_FILTER_KEY)?.let {
            SortOrder.valueOf(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        albumButtons = view.findViewById(R.id.menu_album)

        // Setup refresh handler
        swipeRefresh = view.findViewById(refreshListId)
        swipeRefresh?.setOnRefreshListener {
            handleRefresh()
        }

        setupButtons(view)

        if (useLibraryTrackRows) {
            albumButtons?.isGone = true

            if (navArgs.libraryRoot) {
                filterButtonBar = view.findViewById(R.id.filter_button_bar)
                filterButtonBar?.setOnOrderChangedListener(::setOrderType)
                filterButtonBar?.setOnPrimaryActionClickListener(::onPrimaryAction)
                filterButtonBar?.configureWithCapabilities(viewCapabilities, sortOrder)
            }
        }

        childFragmentManager.setFragmentResultListener(
            ItemSelectionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner,
            ::handleSelectionDialogResult
        )

        registerForContextMenu(listView!!)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            selectionModeBackCallback
        )

        // Register our options menu
        (requireActivity() as MenuHost).addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        // Create a View Manager
        viewManager = LinearLayoutManager(this.context)

        // Hook up the view with the manager and the adapter
        listView = view.findViewById<RecyclerView>(recyclerViewId).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        if (navArgs.isAlbum) {
            viewAdapter.register(
                AlbumDetailHeaderBinder(
                    context = requireContext(),
                    onPlay = { playAll() },
                    onShuffle = { playAll(shuffle = true) },
                    trailingActionIcon = R.drawable.ic_menu_download,
                    trailingActionDescription = R.string.album_download_description,
                    onTrailingAction = { downloadSelectedOrAllTracks() },
                    onInfoAction = ::showAlbumInfo
                )
            )
        } else if (navArgs.playlistId != null) {
            viewAdapter.register(
                AlbumDetailHeaderBinder(
                    context = requireContext(),
                    onPlay = { playAll() },
                    onShuffle = { playAll(shuffle = true) },
                    trailingActionIcon = R.drawable.ic_more_vert,
                    trailingActionDescription = R.string.playlist_menu_description,
                    onTrailingAction = ::showPlaylistHeaderMenu
                )
            )
        } else {
            viewAdapter.register(
                HeaderViewBinder(
                    context = requireContext()
                )
            )
        }

        if (useLibraryTrackRows) {
            viewAdapter.register(
                LibraryTrackBinder(
                    onItemClick = ::onItemClick,
                    onContextMenuClick = ::onContextMenuItemSelected,
                    showHeart = navArgs.getStarred,
                    onHeartClick = ::toggleLibraryHeart
                )
            )
        } else {
            viewAdapter.register(
                TrackViewBinder(
                    onItemClick = { file, _ -> onItemClick(file) },
                    onContextMenuClick = { menu, id -> onContextMenuItemSelected(menu, id) },
                    checkable = true,
                    draggable = false,
                    lifecycleOwner = viewLifecycleOwner,
                    createContextMenu = { itemView, _, downloadState ->
                        val menuRes = if (navArgs.playlistId != null) {
                            R.menu.context_menu_track_collection_playlist
                        } else {
                            R.menu.context_menu_track_collection
                        }
                        Utils.createPopupMenu(itemView, menuRes, downloadState)
                    },
                    layout = if (navArgs.isAlbum) {
                        R.layout.list_item_track_album
                    } else {
                        R.layout.list_item_track
                    },
                    showArtist = if (navArgs.isAlbum) ::albumShowArtist else { _ -> true },
                    showRating = !(navArgs.isAlbum || navArgs.playlistId != null),
                    isSelectionModeActive = { selectionModeActive },
                    onEnterSelectionMode = ::enterSelectionMode
                )
            )
        }

        viewAdapter.register(
            AlbumRowDelegate(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) }
            )
        )

        if (navArgs.isAlbum) {
            viewAdapter.register(
                DiscHeaderBinder(
                    onPlay = ::playDisc,
                    onDownload = { tracks ->
                        DownloadUtil.justDownload(
                            action = DownloadAction.DOWNLOAD,
                            fragment = this,
                            tracks = tracks
                        )
                    }
                )
            )
        }

        // Change the buttons if the status of any selected track changes
        rxBusSubscription += RxBus.trackDownloadStateObservable.subscribe {
            if (it.progress != null) return@subscribe
            val selectedSongs = getSelectedTracks()
            if (!selectedSongs.any { song -> song.id == it.id }) return@subscribe
            triggerButtonUpdate(selectedSongs)
        }

        triggerButtonUpdate()

        // Update the buttons when the selection has changed
        viewAdapter.selectionRevision.observe(
            viewLifecycleOwner
        ) {
            triggerButtonUpdate()
            // Deselecting the last item leaves selection mode automatically, same as tapping
            // every checkbox off in Gmail/Photos-style pickers.
            if (selectionModeActive && viewAdapter.selectedSet.isEmpty()) {
                exitSelectionMode()
            }
        }

        // Attach our onScrollListener
        val scrollListener = object : EndlessScrollListener(viewManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                Timber.w("LOAD MORE")
                // Triggered only when new data needs to be appended to the list
                // Add whatever code is needed to append new items to the bottom of the list
                loadMoreTracks()
            }
        }

        listView!!.addOnScrollListener(scrollListener)

        // Hero title reveal (Album and Playlist detail share the same hero layout): the hero
        // is item 0 of the list (not a persistent scroll view like Artist Detail's), so it gets
        // recycled once scrolled off-screen -- track the scroll offset against a fixed height
        // instead of measuring the live header view.
        if (navArgs.isAlbum || navArgs.playlistId != null) {
            val revealTitle = navArgs.name ?: navArgs.playlistName
            val revealThresholdPx = (
                (HERO_HEIGHT_DP - TOOLBAR_REVEAL_OFFSET_DP) * resources.displayMetrics.density
                ).toInt()
            listView!!.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val collapsed =
                            recyclerView.computeVerticalScrollOffset() >= revealThresholdPx
                        setTitle(if (collapsed) revealTitle else "")
                    }
                }
            )
        }
    }

    private fun toggleLibraryHeart(track: Track) {
        track.starred = !track.starred
        RxBus.ratingSubmitter.onNext(RatingUpdate(track.id, HeartRating(track.starred)))
        if (navArgs.getStarred && !track.starred) {
            listModel.currentList.value = listModel.currentList.value.orEmpty()
                .filterNot { it.id == track.id }
        }
    }

    private fun albumShowArtist(@Suppress("UNUSED_PARAMETER") track: Track): Boolean =
        albumHasMultipleArtists

    // Album notes are optional metadata from a separate, slower network call than the track
    // list, so they can arrive before or after the header is first rendered. Either way, a new
    // AlbumHeader instance is submitted so DiffUtil (which compares AlbumHeader by reference,
    // since it has no equals()/hashCode() override) actually detects the change and rebinds it.
    private fun loadAlbumInfo(albumId: String, forceRefresh: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = listModel.getAlbumInfo(albumId, forceRefresh)
            albumNotes = info?.notes
                ?.let {
                    HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
                }
                ?.takeIf { it.isNotEmpty() }
            refreshHeaderNotes()
        }
    }

    private fun refreshHeaderNotes() {
        val current = viewAdapter.getCurrentList()
        val header = current.firstOrNull() as? AlbumHeader ?: return
        if (header.notes == albumNotes) return

        val updatedHeader = AlbumHeader(header.entries, header.name).apply { notes = albumNotes }
        viewAdapter.submitList(listOf(updatedHeader) + current.drop(1))
    }

    /**
     * Album Detail's Information action. Everything shown here is already resolved by the
     * time this can be tapped - [AlbumDetailHeaderBinder] only
     * shows the button once [AlbumHeader.notes] came back non-empty from [loadAlbumInfo], and the
     * rest (artist/year/song count/disc count/cover) is synchronous, computed when the header
     * was built. No network call happens here.
     */
    private fun showAlbumInfo(header: AlbumHeader) {
        val discCount = header.entries.filterIsInstance<Track>()
            .mapNotNull { it.discNumber }
            .toSet().size
        val coverEntry = header.entries.firstOrNull()

        AlbumInfoBottomSheetFragment.newInstance(
            description = header.notes,
            albumName = header.name,
            artist = header.artists.joinToString(", ").ifEmpty { null },
            year = header.years.singleOrNull()?.toString(),
            songCount = header.childCount,
            discCount = discCount,
            coverArtId = coverEntry?.coverArt,
            coverArtKey = FileUtil.getAlbumArtKey(coverEntry, false)
        ).show(childFragmentManager, AlbumInfoBottomSheetFragment.TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("sort_order", sortOrder?.name)
        outState.putString(SELECTED_ARTIST_ID_KEY, selectedArtistId)
        outState.putString(SELECTED_ARTIST_NAME_KEY, selectedArtistName)
        outState.putString(SELECTED_GENRE_KEY, selectedGenreName)
        outState.putString(PENDING_FILTER_KEY, pendingFilterSelection?.name)
    }

    private fun loadMoreTracks() {
        if (
            displayRandom() ||
            (
                (selectedGenreName != null || navArgs.genreName != null) &&
                    listModel.canLoadMoreGenreSongs
                ) ||
            (selectedArtistId != null && listModel.canLoadMoreArtistSongs) ||
            (displayAllSongs() && listModel.canLoadMoreAllSongs)
        ) {
            getLiveData(append = true)
        }
    }

    internal open fun handleRefresh() {
        getLiveData(refresh = true)
    }

    internal open fun setupButtons(view: View) {
        downloadButton = view.findViewById(R.id.select_album_download)
        addPlaylistButton = view.findViewById(R.id.select_album_add_playlist)

        downloadButton?.setOnClickListener {
            downloadSelectedOrAllTracks()
        }

        addPlaylistButton?.setOnClickListener {
            addTracksToPlaylist(getSelectedTracks())
        }
    }

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onPrepareMenu(menu: Menu) {
            playAllButton = menu.findItem(R.id.select_album_play_all)
            playAllButton?.isVisible = playAllButtonVisible
        }

        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.track_collection_menu, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.select_album_play_all) {
                playAll()
                return true
            }
            return false
        }
    }

    override fun onDestroyView() {
        rxBusSubscription.dispose()
        super.onDestroyView()
    }

    private fun playAll(
        shuffle: Boolean = false,
        insertionMode: MediaPlayerManager.InsertionMode = MediaPlayerManager.InsertionMode.CLEAR
    ) {
        var hasSubFolders = false

        for (item in viewAdapter.getCurrentList()) {
            if (item is MusicDirectory.Child && item.isDirectory) {
                hasSubFolders = true
                break
            }
        }

        val isArtist = navArgs.isArtist

        // Need a valid id to recurse sub directories stuff
        if (hasSubFolders && navArgs.id != null) {
            mediaPlayerManager.playTracksAndToast(
                fragment = this,
                insertionMode = insertionMode,
                id = navArgs.id!!,
                shuffle = shuffle,
                isArtist = isArtist
            )
        } else {
            mediaPlayerManager.suggestedPlaylistName = navArgs.playlistName
            mediaPlayerManager.addToPlaylist(
                songs = getAllTracks(),
                insertionMode = insertionMode,
                autoPlay = (insertionMode != MediaPlayerManager.InsertionMode.APPEND),
                shuffle = shuffle
            )
        }
    }
    /**
     * Disc header's Play button. [tracks] is
     * [org.moire.ultrasonic.adapters.DiscHeader.tracks], already scoped to a single disc and in
     * track-number order (falling back to path for incomplete metadata) by
     * [EntryByDiscAndTrackComparator], which [defaultObserver] already sorts the whole album
     * with before grouping by disc -- offline, that list is also already restricted to locally
     * playable tracks, since [org.moire.ultrasonic.service.OfflineMusicService.getAlbumAsDir]
     * only returns downloaded ones, so a disc header only exists here at all when it has at
     * least one. Reuses [MediaPlayerManager.addToPlaylist] directly (same as the album header's
     * Play button) instead of a separate playback path, which is also where the existing
     * double-tap guard (`addToPlaylistDuplicateGuard`) and background queue-building already
     * live.
     */
    private fun playDisc(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            toast(R.string.album_play_disc_empty_offline)
            return
        }
        mediaPlayerManager.addToPlaylist(
            songs = tracks,
            autoPlay = true,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR
        )
    }

    private fun downloadSelectedOrAllTracks() {
        DownloadUtil.justDownload(
            action = DownloadAction.DOWNLOAD,
            fragment = this,
            tracks = getSelectedOrAllTracks()
        )
    }

    /**
     * Enters selection mode (checkbox + row-action icons swap for a checkbox on every row) and
     * selects [track]. Only triggered by a long-press (see [org.moire.ultrasonic.adapters.TrackViewBinder]);
     * tapping a row plays it directly otherwise.
     */
    private val selectionModeBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewAdapter.setSelectionStatusOfAll(false)
            exitSelectionMode()
        }
    }

    private fun enterSelectionMode(track: Track) {
        if (selectionModeActive) return
        selectionModeActive = true
        selectionModeBackCallback.isEnabled = true
        viewAdapter.notifySelected(track.longId)
        viewAdapter.notifyItemRangeChanged(0, viewAdapter.itemCount)
    }

    private fun exitSelectionMode() {
        if (!selectionModeActive) return
        selectionModeActive = false
        selectionModeBackCallback.isEnabled = false
        viewAdapter.notifyItemRangeChanged(0, viewAdapter.itemCount)
    }

    private fun addTracksToPlaylist(tracks: List<Track>) {
        if (tracks.isEmpty() || isOffline()) return
        pendingAddToPlaylistTracks = tracks
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            availablePlaylists = PlaylistUtil.getPlaylists()
            if (availablePlaylists.isEmpty()) {
                pendingAddToPlaylistTracks = null
                toast(R.string.select_playlist_empty)
                return@launch
            }
            ItemSelectionDialogFragment.create(
                R.string.playlist_add_to_title,
                availablePlaylists.map { it.name }.toTypedArray()
            ).show(childFragmentManager, ItemSelectionDialogFragment.TAG)
        }
    }

    private fun handleAddToPlaylistResult(
        @Suppress("UNUSED_PARAMETER") key: String,
        bundle: Bundle
    ) {
        val tracks = pendingAddToPlaylistTracks
        pendingAddToPlaylistTracks = null
        if (bundle.getBoolean(ItemSelectionDialogFragment.RESULT_CANCELLED) ||
            tracks == null
        ) {
            return
        }

        val name = bundle.getString(ItemSelectionDialogFragment.RESULT_SELECTED_ITEM) ?: return
        val playlist = availablePlaylists.firstOrNull { it.name == name } ?: return

        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(getString(R.string.playlist_add_error))
        ) {
            PlaylistUtil.addToPlaylist(playlist, tracks)
            toast(getString(R.string.playlist_added, playlist.name))
            viewAdapter.setSelectionStatusOfAll(false)
            exitSelectionMode()
        }
    }

    /*
     * Playlist detail hero's trailing ⋮ action (download, rename, delete). Reuses
     * ItemSelectionDialogFragment, the same shared list
     * dialog already used for artist/genre filter selection above, instead of a PopupMenu that
     * would need the clicked view as an anchor.
     */
    private fun showPlaylistHeaderMenu() {
        if (navArgs.playlistId == null) return
        // Download/rename/delete all require updatePlaylist()/deletePlaylist(), which throw
        // OfflineException outright - nothing in this menu is actually usable offline.
        if (isOffline()) {
            toast(R.string.playlist_menu_unavailable_offline)
            return
        }
        pendingPlaylistHeaderMenu = true
        showSelectionDialog(
            R.string.playlist_menu_description,
            arrayOf(
                getString(R.string.common_download),
                getString(R.string.playlist_rename_action),
                getString(R.string.common_delete)
            )
        )
    }

    private fun handlePlaylistHeaderMenuResult(bundle: Bundle) {
        pendingPlaylistHeaderMenu = false
        if (bundle.getBoolean(ItemSelectionDialogFragment.RESULT_CANCELLED)) return

        when (bundle.getString(ItemSelectionDialogFragment.RESULT_SELECTED_ITEM)) {
            getString(R.string.common_download) -> downloadSelectedOrAllTracks()
            getString(R.string.playlist_rename_action) -> showRenamePlaylistDialog()
            getString(R.string.common_delete) -> confirmDeletePlaylist()
        }
    }

    private fun showRenamePlaylistDialog() {
        val playlistId = navArgs.playlistId ?: return
        val dialogView = layoutInflater.inflate(R.layout.create_playlist, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.create_playlist_name)
        nameInput.setText(navArgs.playlistName)
        val inputLayout = dialogView as TextInputLayout
        val dialog = ConfirmationDialog.Builder(requireContext())
            .setTitle(R.string.playlist_rename_action)
            .setView(dialogView)
            .setPositiveButton(R.string.common_ok, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    inputLayout.error = getString(R.string.playlist_name_required)
                } else {
                    dialog.dismiss()
                    renamePlaylist(playlistId, name)
                }
            }
        }
        dialog.show()
    }

    private fun renamePlaylist(id: String, name: String) {
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(getString(R.string.playlist_updated_info_error, name))
        ) {
            withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().updatePlaylist(id, name, null, null)
            }
            setTitle(name)
            toast(getString(R.string.playlist_updated_info, name))
        }
    }

    private fun confirmDeletePlaylist() {
        val playlistId = navArgs.playlistId ?: return
        val name = navArgs.playlistName.orEmpty()
        ConfirmationDialog.Builder(requireContext())
            .setIcon(R.drawable.ic_baseline_warning)
            .setTitle(R.string.common_confirm)
            .setMessage(getString(R.string.delete_playlist, name))
            .setPositiveButton(R.string.common_ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(
                    toastingExceptionHandler(getString(R.string.menu_deleted_playlist_error, name))
                ) {
                    withContext(Dispatchers.IO) {
                        MusicServiceFactory.getMusicService().deletePlaylist(playlistId)
                    }
                    toast(getString(R.string.menu_deleted_playlist, name))
                    findNavController().navigateUp()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    @Synchronized
    fun triggerButtonUpdate(selection: List<Track> = getSelectedTracks()) {
        listModel.calculateButtonState(selection, ::updateButtonState)
    }

    private fun updateButtonState(show: TrackCollectionModel.Companion.ButtonStates) {
        // We are coming back from unknown context
        // and need to ensure Main Thread in order to manipulate the UI
        // If view is null, our view was disposed in the meantime
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            downloadButton?.isVisible = show.all && show.download && !isOffline()
            addPlaylistButton?.isVisible = show.all && !isOffline()
        }
    }

    /**
     * Result of [buildDisplayList]: the sort, per-disc grouping and hero-header construction are
     * pure data transforms with no Android API calls, so on a large album (Bach 333, 5,517
     * tracks: measured ~60-80ms) they're moved off the main thread instead of running inline in
     * the LiveData observer, which was blocking a frame on every open -- cold *and* from cache.
     */
    private data class ProcessedTrackList(
        val displayList: List<Identifiable>,
        val isEmpty: Boolean,
        val songCount: Int,
        val allVideos: Boolean,
        val hasMultipleArtists: Boolean,
        val hasMultipleDiscs: Boolean
    )

    private suspend fun buildDisplayList(
        children: List<MusicDirectory.Child>
    ): ProcessedTrackList = withContext(Dispatchers.Default) {
        val entryList: MutableList<MusicDirectory.Child> = children.toMutableList()

        if (listModel.currentListIsSortable && Settings.SHOULD_SORT_BY_DISC) {
            Collections.sort(entryList, EntryByDiscAndTrackComparator())
        }

        var allVideos = true
        var songCount = 0

        for (entry in entryList) {
            if (!entry.isVideo) {
                allVideos = false
            }
            if (!entry.isDirectory) {
                songCount++
            }
        }

        var hasMultipleArtists = albumHasMultipleArtists
        var hasMultipleDiscs = albumHasMultipleDiscs

        val displayList: List<Identifiable> = if (songCount > 0 && listModel.showHeader) {
            val intentAlbumName = navArgs.name ?: navArgs.playlistName
            val albumHeader = AlbumHeader(children, intentAlbumName)

            if (navArgs.isAlbum) {
                hasMultipleArtists = albumHeader.artists.size > 1
                hasMultipleDiscs = entryList.filterIsInstance<Track>()
                    .mapNotNull { track -> track.discNumber }
                    .toSet().size > 1
                albumHeader.notes = albumNotes
            }

            val mixedList: MutableList<Identifiable> = mutableListOf(albumHeader)
            if (navArgs.isAlbum && hasMultipleDiscs) {
                val tracksByDisc = entryList.filterIsInstance<Track>()
                    .groupBy { it.discNumber ?: 1 }
                var lastDisc: Int? = null
                for (entry in entryList) {
                    val discNumber = (entry as? Track)?.discNumber ?: 1
                    if (discNumber != lastDisc) {
                        mixedList.add(DiscHeader(discNumber, tracksByDisc[discNumber].orEmpty()))
                        lastDisc = discNumber
                    }
                    mixedList.add(entry)
                }
            } else {
                mixedList.addAll(entryList)
            }
            mixedList
        } else {
            entryList
        }

        ProcessedTrackList(
            displayList = displayList,
            isEmpty = entryList.isEmpty(),
            songCount = songCount,
            allVideos = allVideos,
            hasMultipleArtists = hasMultipleArtists,
            hasMultipleDiscs = hasMultipleDiscs
        )
    }

    override val defaultObserver: (List<MusicDirectory.Child>) -> Unit = { children ->

        Timber.i("Received list")

        viewLifecycleOwner.lifecycleScope.launch {
            val sortToken = PerfMetrics.start(
                "track_collection:sort_and_group:count=${children.size}"
            )
            val processed = buildDisplayList(children)
            PerfMetrics.end(
                "track_collection:sort_and_group:count=${children.size}",
                sortToken
            )

            albumHasMultipleArtists = processed.hasMultipleArtists
            albumHasMultipleDiscs = processed.hasMultipleDiscs

            // Show a text if we have no entries
            emptyView.isVisible = processed.isEmpty

            triggerButtonUpdate()

            val isAlbumList = (navArgs.albumListType != null)

            // Album/Playlist Detail have their own persistent play/shuffle row in the hero, so
            // the overflow menu's "play all" would be redundant there.
            playAllButtonVisible = !useLibraryTrackRows &&
                !(navArgs.isAlbum || navArgs.playlistId != null) &&
                !(isAlbumList || processed.isEmpty) && !processed.allVideos

            playAllButton?.isVisible = playAllButtonVisible

            viewAdapter.submitList(processed.displayList)

            val playAll = navArgs.autoPlay

            if (playAll && processed.songCount > 0) {
                playAll(navArgs.shuffle, MediaPlayerManager.InsertionMode.CLEAR)
            }

            listModel.currentListIsSortable = true

            Timber.i("Processed list")
        }
    }

    internal fun getSelectedTracks(): List<Track> {
        // Walk through selected set and get the Entries based on the saved ids.
        return viewAdapter.getCurrentList().mapNotNull {
            if (it is Track && viewAdapter.isSelected(it.longId)) {
                it
            } else {
                null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAllTracks(): List<Track> = viewAdapter.getCurrentList().filter {
        it is Track && !it.isDirectory
    } as List<Track>

    fun getSelectedOrAllTracks(): List<Track> = getSelectedTracks().ifEmpty {
        getAllTracks()
    }

    override fun setTitle(title: String?) {
        setTitle(this@TrackCollectionFragment, title)
    }

    fun setTitle(id: Int) {
        setTitle(this@TrackCollectionFragment, id)
    }

    @Suppress("LongMethod")
    override fun getLiveData(
        refresh: Boolean,
        append: Boolean
    ): LiveData<List<MusicDirectory.Child>> {
        Timber.i("Starting gathering track collection data...")
        val id = navArgs.id
        val isAlbum = navArgs.isAlbum
        val name = navArgs.name
        val playlistId = navArgs.playlistId
        val playlistName = navArgs.playlistName
        val shareId = navArgs.shareId
        val shareName = navArgs.shareName
        val genreName = selectedGenreName ?: navArgs.genreName
        val artistId = selectedArtistId
        val artistName = selectedArtistName

        val getStarredTracks = displayStarred()
        val getVideos = navArgs.getVideos
        val getRandomTracks = displayRandom()
        val getDailyMix = navArgs.dailyMix
        val size = if (navArgs.size < 0) Settings.MAX_SONGS else navArgs.size
        val offset = navArgs.offset
        val refresh2 = navArgs.refresh || refresh

        if (useLibraryTrackRows) {
            listModel.showHeader = false
        }

        // Cancel any still-running load from a previous filter/sort change so a slow, stale
        // response can't overwrite the list after a more recent selection already completed.
        loadJob?.cancel()
        loadJob = listModel.viewModelScope.launch(
            toastingExceptionHandler()
        ) {
            swipeRefresh?.isRefreshing = true

            if (getDailyMix) {
                setTitle(R.string.home_mix_title)
                listModel.getDailyMix()
            } else if (playlistId != null) {
                // Playlist Detail reveals its title in the toolbar only once the hero has been
                // scrolled past (see the OnScrollListener added in onViewCreated).
                setTitle("")
                listModel.getPlaylist(playlistId, playlistName!!)
            } else if (shareId != null) {
                setTitle(shareName)
                listModel.getShare(shareId)
            } else if (artistId != null && artistName != null) {
                setTitle(artistName)
                listModel.getSongsForArtist(artistId, artistName, size, append)
            } else if (genreName != null) {
                setTitle(genreName)
                listModel.getSongsForGenre(genreName, size, offset, append)
            } else if (getStarredTracks) {
                setTitle(getString(R.string.main_songs_starred))
                listModel.getStarred()
            } else if (getVideos) {
                setTitle(R.string.main_videos)
                listModel.getVideos(refresh2)
            } else if (displayAllSongs()) {
                setTitle(R.string.main_songs_title)
                listModel.getAllSongs(size, offset, append)
            } else if (id == null || getRandomTracks) {
                // There seems to be a bug in ViewPager when resuming the Activity that sub-fragments
                // arguments are empty. If we have no id, just show some random tracks
                setTitle(R.string.main_songs_random)
                listModel.getRandom(size, append)
            } else {
                // Album Detail reveals its title in the toolbar only once the hero has been
                // scrolled past (see the OnScrollListener added in onViewCreated).
                setTitle(if (isAlbum) "" else name)

                if (isAlbum && ActiveServerProvider.shouldUseId3Tags()) {
                    listModel.getAlbum(refresh2, id, name)
                } else {
                    listModel.getMusicDirectory(refresh2, id, name)
                }

                if (isAlbum) loadAlbumInfo(id, refresh2)
            }

            swipeRefresh?.isRefreshing = false
        }
        return listModel.currentList
    }

    private fun displayStarred() = (sortOrder == SortOrder.STARRED) || navArgs.getStarred

    private fun displayRandom() = (sortOrder == SortOrder.RANDOM) || navArgs.getRandom

    private fun displayAllSongs() = sortOrder == SortOrder.ALL_SONGS

    override fun onContextMenuItemSelected(
        menuItem: MenuItem,
        item: MusicDirectory.Child
    ): Boolean {
        if (menuItem.itemId == R.id.song_menu_play_from_here && item is Track) {
            return playFromHere(item)
        }

        if (menuItem.itemId == R.id.song_menu_add_playlist && item is Track) {
            addTracksToPlaylist(listOf(item))
            return true
        }

        if (menuItem.itemId == R.id.song_menu_remove_from_playlist && item is Track) {
            return removeFromPlaylist(item)
        }

        val tracks = getClickedSong(item)

        return ContextMenuUtil.handleContextMenuTracks(
            menuItem = menuItem,
            tracks = tracks,
            mediaPlayerManager = mediaPlayerManager,
            fragment = this
        )
    }

    private fun playFromHere(track: Track): Boolean {
        PerfMetrics.mark("play_tap")
        val allTracks = getAllTracks()
        val startIndex = allTracks.indexOfFirst { it === track }
        if (startIndex < 0) return false

        mediaPlayerManager.addToPlaylist(
            songs = allTracks,
            autoPlay = false,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            startIndex = startIndex
        )
        return true
    }

    /*
     * "Quitar de esta playlist" - distinct from deleting a download or the song itself.
     * updatePlaylist.view's songIndexesToRemove is 0-based and
     * positional within the playlist's current order, so the index has to come from the same
     * getAllTracks() ordering used for playback, not from the track's id (playlists can contain
     * the same song more than once).
     */
    private fun removeFromPlaylist(track: Track): Boolean {
        val playlistId = navArgs.playlistId ?: return false
        val index = getAllTracks().indexOfFirst { it === track }
        if (index < 0) return false

        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(getString(R.string.playlist_remove_error))
        ) {
            withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService()
                    .updatePlaylist(playlistId, null, null, null, listOf(index))
            }
            listModel.currentList.value = listModel.currentList.value.orEmpty()
                .filterNot { it === track }
            toast(
                getString(
                    R.string.playlist_removed_from_playlist,
                    track.title ?: track.name.orEmpty()
                )
            )
        }
        return true
    }

    private fun getClickedSong(item: MusicDirectory.Child): List<Track> {
        // This can probably be done better
        return viewAdapter.getCurrentList().mapNotNull {
            if (it is Track && (it.id == item.id)) {
                it
            } else {
                null
            }
        }
    }

    override fun onItemClick(item: MusicDirectory.Child) {
        when {
            item.isDirectory -> {
                val action = NavigationGraphDirections.toTrackCollection(
                    id = item.id,
                    isAlbum = true,
                    name = item.title,
                    parentId = item.parent
                )
                findNavController().navigate(action)
            }

            item is Track && item.isVideo -> {
                VideoPlayer.playVideo(requireContext(), item)
            }

            item is Track -> playFromHere(item)

            else -> triggerButtonUpdate()
        }
    }

    override fun setOrderType(newOrder: SortOrder) {
        sortOrder = newOrder
        viewAdapter.setSelectionStatusOfAll(false)

        when (newOrder) {
            SortOrder.BY_ARTIST -> showArtistSelection()

            SortOrder.BY_GENRE -> showGenreSelection()

            else -> {
                selectedArtistId = null
                selectedArtistName = null
                selectedGenreName = null
                getLiveData(refresh = true)
            }
        }
    }

    override fun getOrderType(): SortOrder? = sortOrder

    override fun onPrimaryAction() {
        if (getAllTracks().isNotEmpty()) playAll()
    }

    override var viewCapabilities: ViewCapabilities = ViewCapabilities(
        supportsGrid = false,
        supportedSortOrders = getListOfSortOrders(),
        primaryAction = FilterPrimaryAction.PLAY_ALL
    )

    private fun getListOfSortOrders(): List<SortOrder> {
        val isOnline = !isOffline()
        val supported = mutableListOf(
            SortOrder.ALL_SONGS,
            SortOrder.RANDOM,
            SortOrder.BY_ARTIST,
            SortOrder.BY_GENRE
        )

        if (isOnline) {
            supported.add(SortOrder.STARRED)
        }
        return supported
    }

    private fun showArtistSelection() {
        pendingFilterSelection = SortOrder.BY_ARTIST
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            swipeRefresh?.isRefreshing = true
            availableArtists = try {
                listModel.getArtists(true).sortedBy {
                    it.name.orEmpty().lowercase(Locale.ROOT)
                }
            } finally {
                swipeRefresh?.isRefreshing = false
            }

            showSelectionDialog(
                R.string.main_artists_title,
                availableArtists.mapNotNull { it.name }.toTypedArray()
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = if (navArgs.libraryRoot) R.layout.list_layout_track_filterable else mainLayout
        return inflater.inflate(layout, container, false)
    }

    private fun showGenreSelection() {
        pendingFilterSelection = SortOrder.BY_GENRE
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            swipeRefresh?.isRefreshing = true
            val genres = try {
                listModel.getGenres(true)
            } finally {
                swipeRefresh?.isRefreshing = false
            }

            showSelectionDialog(
                R.string.main_genres_title,
                genres.map { it.name }.sorted().toTypedArray()
            )
        }
    }

    private fun showSelectionDialog(title: Int, items: Array<String>) {
        if (items.isEmpty()) return
        if (childFragmentManager.findFragmentByTag(ItemSelectionDialogFragment.TAG) == null) {
            ItemSelectionDialogFragment.create(title, items)
                .show(childFragmentManager, ItemSelectionDialogFragment.TAG)
        }
    }

    private fun handleSelectionDialogResult(requestKey: String, bundle: Bundle) {
        if (pendingAddToPlaylistTracks != null) {
            handleAddToPlaylistResult(requestKey, bundle)
        } else if (pendingPlaylistHeaderMenu) {
            handlePlaylistHeaderMenuResult(bundle)
        } else {
            handleFilterSelectionResult(bundle)
        }
    }

    private fun handleFilterSelectionResult(bundle: Bundle) {
        if (bundle.getBoolean(ItemSelectionDialogFragment.RESULT_CANCELLED)) {
            pendingFilterSelection = null
            swipeRefresh?.isRefreshing = false
            return
        }

        val selectedName = bundle.getString(ItemSelectionDialogFragment.RESULT_SELECTED_ITEM)
            ?: return
        when (pendingFilterSelection) {
            SortOrder.BY_ARTIST -> applyArtistFilter(selectedName)

            SortOrder.BY_GENRE -> {
                selectedArtistId = null
                selectedArtistName = null
                selectedGenreName = selectedName
                pendingFilterSelection = null
                getLiveData(refresh = true)
            }

            else -> Unit
        }
    }

    private fun applyArtistFilter(artistName: String) {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val artist = availableArtists.firstOrNull { it.name == artistName }
                ?: listModel.getArtists(false).firstOrNull { it.name == artistName }
                ?: return@launch

            selectedArtistId = artist.id
            selectedArtistName = artist.name
            selectedGenreName = null
            pendingFilterSelection = null
            getLiveData(refresh = true)
        }
    }

    companion object {
        private const val SELECTED_ARTIST_ID_KEY = "songs_selected_artist_id"
        private const val SELECTED_ARTIST_NAME_KEY = "songs_selected_artist_name"
        private const val SELECTED_GENRE_KEY = "songs_selected_genre"
        private const val PENDING_FILTER_KEY = "songs_pending_filter"
        private const val HERO_HEIGHT_DP = 300
        private const val TOOLBAR_REVEAL_OFFSET_DP = 56
    }
}
