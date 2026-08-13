/*
 * CreatePlaylistFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.adapters.PlaylistTrackPickerBinder
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.SearchListModel
import org.moire.ultrasonic.model.TrackCollectionModel
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.FilterButtonBar
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities

/** Selects songs and creates a new server playlist only when the selection is saved. */
class CreatePlaylistFragment : Fragment() {

    private val navArgs: CreatePlaylistFragmentArgs by navArgs()
    private val trackModel: TrackCollectionModel by viewModels()
    private val searchModel: SearchListModel by viewModels()
    private val trackAdapter = BaseAdapter<Track>()
    private val selectedTracks = linkedMapOf<String, Track>()

    private var progress: ProgressBar? = null
    private var emptyText: TextView? = null
    private var selectedCount: TextView? = null
    private var saveButton: MaterialButton? = null
    private var searchInput: TextInputEditText? = null
    private var pendingSelection: SortOrder? = null
    private var availableArtists: List<ArtistOrIndex> = emptyList()
    private var loadJob: Job? = null

    // Tracks what the list is currently showing so loadMoreTracks() (triggered by scroll, with
    // no arguments of its own) knows which paginated call to repeat with append=true.
    private var currentOrder: SortOrder = SortOrder.ALL_SONGS
    private var selectedArtist: ArtistOrIndex? = null
    private var selectedGenreName: String? = null
    private var showingSearchResults = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.create_playlist_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, navArgs.playlistName)

        progress = view.findViewById(R.id.playlist_song_progress)
        emptyText = view.findViewById(R.id.playlist_song_empty)
        selectedCount = view.findViewById(R.id.playlist_selected_count)
        saveButton = view.findViewById(R.id.playlist_save)
        searchInput = view.findViewById(R.id.playlist_song_search)

        trackAdapter.register(
            PlaylistTrackPickerBinder(
                isSelected = { selectedTracks.containsKey(it.id) },
                onSelectionChanged = ::toggleTrack
            )
        )
        view.findViewById<RecyclerView>(R.id.playlist_song_list).apply {
            val linearLayoutManager = LinearLayoutManager(requireContext())
            layoutManager = linearLayoutManager
            adapter = trackAdapter
            addOnScrollListener(
                object : EndlessScrollListener(linearLayoutManager) {
                    override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                        loadMoreTracks()
                    }
                }
            )
        }

        setupSearch(view)
        setupFilters(view)
        setupSelectionDialogs()
        saveButton?.setOnClickListener { savePlaylist() }
        updateSelectionState()

        trackModel.currentList.observe(viewLifecycleOwner) { items ->
            showTracks(items.filterIsInstance<Track>())
        }
        loadOrder(SortOrder.ALL_SONGS)
    }

    private fun setupSearch(view: View) {
        val searchLayout = view.findViewById<TextInputLayout>(R.id.playlist_song_search_layout)
        searchLayout.setEndIconOnClickListener { runSearch() }
        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }
    }

    private fun setupFilters(view: View) {
        view.findViewById<FilterButtonBar>(R.id.playlist_song_filter_bar).apply {
            setOnOrderChangedListener(::onOrderChanged)
            configureWithCapabilities(
                ViewCapabilities(
                    supportsGrid = false,
                    supportedSortOrders = listOf(
                        SortOrder.ALL_SONGS,
                        SortOrder.RANDOM,
                        SortOrder.BY_ARTIST,
                        SortOrder.BY_GENRE
                    )
                ),
                SortOrder.ALL_SONGS
            )
        }
    }

    private fun setupSelectionDialogs() {
        childFragmentManager.setFragmentResultListener(
            ItemSelectionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(ItemSelectionDialogFragment.RESULT_CANCELLED)) {
                pendingSelection = null
                setLoading(false)
                return@setFragmentResultListener
            }

            val selected = bundle.getString(ItemSelectionDialogFragment.RESULT_SELECTED_ITEM)
                ?: return@setFragmentResultListener
            when (pendingSelection) {
                SortOrder.BY_ARTIST -> loadArtist(selected)
                SortOrder.BY_GENRE -> loadGenre(selected)
                else -> Unit
            }
            pendingSelection = null
        }
    }

    private fun onOrderChanged(order: SortOrder) {
        searchInput?.setText("")
        Util.hideKeyboard(activity)
        when (order) {
            SortOrder.BY_ARTIST -> showArtistSelection()
            SortOrder.BY_GENRE -> showGenreSelection()
            else -> loadOrder(order)
        }
    }

    private fun loadOrder(order: SortOrder) {
        currentOrder = order
        showingSearchResults = false
        // Cancel any still-running load so a slow, stale response can't overwrite the list
        // after the user has already switched to a different filter or search.
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            setLoading(true)
            when (order) {
                SortOrder.ALL_SONGS -> trackModel.getAllSongs(
                    Settings.MAX_SONGS,
                    offset = 0,
                    append = false
                )

                SortOrder.RANDOM -> trackModel.getRandom(Settings.MAX_SONGS, append = false)

                else -> Unit
            }
        }
    }

    /**
     * Triggered by [EndlessScrollListener] as the user scrolls near the bottom. Repeats
     * whichever load [currentOrder] represents with append=true, using the same
     * [TrackCollectionModel] pagination (canLoadMoreAllSongs/canLoadMoreArtistSongs/
     * canLoadMoreGenreSongs) already relied on by TrackCollectionFragment - this screen just
     * never called it, which is why "All Songs" only ever showed the first
     * [Settings.MAX_SONGS] tracks.
     */
    private fun loadMoreTracks() {
        if (showingSearchResults || loadJob?.isActive == true) return
        loadJob = viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            when (currentOrder) {
                SortOrder.ALL_SONGS -> {
                    if (!trackModel.canLoadMoreAllSongs) return@launch
                    setLoading(true)
                    trackModel.getAllSongs(Settings.MAX_SONGS, offset = 0, append = true)
                }

                SortOrder.RANDOM -> {
                    setLoading(true)
                    trackModel.getRandom(Settings.MAX_SONGS, append = true)
                }

                SortOrder.BY_ARTIST -> {
                    val artist = selectedArtist ?: return@launch
                    if (!trackModel.canLoadMoreArtistSongs) return@launch
                    setLoading(true)
                    trackModel.getSongsForArtist(
                        artist.id,
                        artist.name.orEmpty(),
                        Settings.MAX_SONGS,
                        append = true
                    )
                }

                SortOrder.BY_GENRE -> {
                    val genre = selectedGenreName ?: return@launch
                    if (!trackModel.canLoadMoreGenreSongs) return@launch
                    setLoading(true)
                    trackModel.getSongsForGenre(genre, Settings.MAX_SONGS, offset = 0, append = true)
                }

                else -> Unit
            }
        }
    }

    private fun runSearch() {
        val query = searchInput?.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            loadOrder(SortOrder.ALL_SONGS)
            return
        }
        showingSearchResults = true
        Util.hideKeyboard(activity)
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            setLoading(true)
            val songs = searchModel.search(query)?.songs.orEmpty()
            showTracks(songs)
        }
    }

    private fun showArtistSelection() {
        pendingSelection = SortOrder.BY_ARTIST
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            setLoading(true)
            availableArtists = trackModel.getArtists(true).sortedBy {
                it.name.orEmpty().lowercase(Locale.ROOT)
            }
            setLoading(false)
            showSelectionDialog(
                R.string.main_artists_title,
                availableArtists.mapNotNull { it.name }.toTypedArray()
            )
        }
    }

    private fun showGenreSelection() {
        pendingSelection = SortOrder.BY_GENRE
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            setLoading(true)
            val genres = trackModel.getGenres(true).map { it.name }.sorted().toTypedArray()
            setLoading(false)
            showSelectionDialog(R.string.main_genres_title, genres)
        }
    }

    private fun showSelectionDialog(title: Int, items: Array<String>) {
        if (items.isEmpty()) return
        if (childFragmentManager.findFragmentByTag(ItemSelectionDialogFragment.TAG) == null) {
            ItemSelectionDialogFragment.create(title, items)
                .show(childFragmentManager, ItemSelectionDialogFragment.TAG)
        }
    }

    private fun loadArtist(name: String) {
        val artist = availableArtists.firstOrNull { it.name == name } ?: return
        currentOrder = SortOrder.BY_ARTIST
        selectedArtist = artist
        showingSearchResults = false
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            setLoading(true)
            trackModel.getSongsForArtist(artist.id, name, Settings.MAX_SONGS, append = false)
        }
    }

    private fun loadGenre(name: String) {
        currentOrder = SortOrder.BY_GENRE
        selectedGenreName = name
        showingSearchResults = false
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            setLoading(true)
            trackModel.getSongsForGenre(name, Settings.MAX_SONGS, offset = 0, append = false)
        }
    }

    private fun showTracks(tracks: List<Track>) {
        trackAdapter.submitList(tracks)
        emptyText?.isVisible = tracks.isEmpty()
        setLoading(false)
    }

    private fun toggleTrack(track: Track) {
        if (selectedTracks.remove(track.id) == null) {
            selectedTracks[track.id] = track
        }
        updateSelectionState()
    }

    private fun updateSelectionState() {
        val count = selectedTracks.size
        selectedCount?.text = resources.getQuantityString(
            R.plurals.playlist_selected_songs,
            count,
            count
        )
        saveButton?.isEnabled = count > 0
    }

    private fun savePlaylist() {
        val tracks = selectedTracks.values.toList()
        if (tracks.isEmpty()) {
            toast(R.string.playlist_select_song)
            return
        }
        saveButton?.isEnabled = false
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(getString(R.string.playlist_create_error))
        ) {
            withContext(Dispatchers.IO) {
                getMusicService().createPlaylist(null, navArgs.playlistName, tracks)
            }
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                PLAYLIST_CREATED_RESULT,
                true
            )
            toast(R.string.playlist_created)
            findNavController().popBackStack()
        }
    }

    private fun setLoading(loading: Boolean) {
        progress?.isVisible = loading
    }

    companion object {
        const val PLAYLIST_CREATED_RESULT = "playlist_created_result"
    }
}
