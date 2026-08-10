/*
 * SearchFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.provider.SearchRecentSuggestions
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumRowDelegate
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.adapters.DividerBinder
import org.moire.ultrasonic.adapters.MoreButtonBinder
import org.moire.ultrasonic.adapters.MoreButtonBinder.MoreButton
import org.moire.ultrasonic.adapters.TrackViewBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Identifiable
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.SearchListModel
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.util.ContextMenuUtil.handleContextMenu
import org.moire.ultrasonic.util.ContextMenuUtil.handleContextMenuTracks
import org.moire.ultrasonic.util.RefreshableFragment
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler

private const val LIVE_SEARCH_DEBOUNCE_MS = 400L
private const val LIVE_SEARCH_MIN_QUERY_LENGTH = 2

/**
 * Initiates a search on the media library and displays the results

 */
class SearchFragment :
    MultiListFragment<Identifiable>(),
    KoinScopeComponent,
    RefreshableFragment {
    private var searchResult: SearchResult? = null
    private var searchJob: Job? = null
    private var liveSearchJob: Job? = null
    private var lastLiveSearchQuery: String? = null
    override var swipeRefresh: SwipeRefreshLayout? = null
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val navArgs by navArgs<SearchFragmentArgs>()
    override val listModel: SearchListModel by viewModels()
    override val mainLayout: Int = R.layout.search

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.search_title)
        emptyView.findViewById<ImageView>(R.id.empty_list_icon)
            .setImageResource(R.drawable.ic_menu_search)
        setupSearchField(view)
        showSearchPrompt()

        listModel.searchResult.observe(
            viewLifecycleOwner
        ) {
            if (it != null) {
                // Shorten the display initially
                searchResult = it
                populateList(listModel.trimResultLength(it))
            }
        }

        swipeRefresh = view.findViewById(R.id.swipe_refresh_view)
        swipeRefresh!!.isEnabled = false

        registerForContextMenu(listView!!)

        // Register our data binders
        // IMPORTANT:
        // They need to be added in the order of most specific -> least specific.
        viewAdapter.register(
            ArtistRowBinder(
                onItemClick = ::onItemClick,
                onContextMenuClick = ::onContextMenuItemSelected,
                enableSections = false
            )
        )

        viewAdapter.register(
            AlbumRowDelegate(
                onItemClick = ::onItemClick,
                onContextMenuClick = ::onContextMenuItemSelected
            )
        )

        viewAdapter.register(
            TrackViewBinder(
                onItemClick = { file, _ -> onItemClick(file) },
                onContextMenuClick = ::onContextMenuItemSelected,
                checkable = false,
                draggable = false,
                lifecycleOwner = viewLifecycleOwner,
                showRating = false
            )
        )

        viewAdapter.register(
            DividerBinder()
        )

        viewAdapter.register(
            MoreButtonBinder()
        )

        // If the fragment was started with a query (e.g. from voice search),
        // try to execute search right away
        if (navArgs.query != null) {
            return search(navArgs.query!!, navArgs.autoplay)
        }
    }

    override fun onDestroyView() {
        liveSearchJob?.cancel()
        Util.hideKeyboard(activity)
        super.onDestroyView()
    }

    private fun setupSearchField(view: View) {
        val searchView = view.findViewById<SearchView>(R.id.search_field)
        val searchManager = requireContext().getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
        searchView.setIconifiedByDefault(false)
        searchView.isIconified = false

        navArgs.query?.let {
            searchView.setQuery(it, false)
            searchView.clearFocus()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val submitted = query?.trim().orEmpty()
                if (submitted.isEmpty()) return true
                SearchRecentSuggestions(
                    requireContext(),
                    SearchSuggestionProvider.AUTHORITY,
                    SearchSuggestionProvider.MODE
                ).saveRecentQuery(submitted, null)
                search(submitted, false)
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                scheduleLiveSearch(newText)
                return true
            }
        })
    }

    private fun scheduleLiveSearch(text: String?) {
        val query = text?.trim().orEmpty()
        liveSearchJob?.cancel()
        if (query.isEmpty()) {
            lastLiveSearchQuery = null
            searchResult = null
            viewAdapter.submitList(emptyList())
            showSearchPrompt()
            return
        }
        if (query.length < LIVE_SEARCH_MIN_QUERY_LENGTH || query == lastLiveSearchQuery) return
        liveSearchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(LIVE_SEARCH_DEBOUNCE_MS)
            lastLiveSearchQuery = query
            search(query, false)
        }
    }

    private fun search(query: String, autoplay: Boolean) {
        // Live search can re-trigger this before a previous request finishes (e.g. two
        // debounced queries in flight if the network is slow) -- cancel the older one so its
        // response can't land after and overwrite a newer query's results.
        searchJob?.cancel()
        emptyView.isVisible = false
        searchJob = listModel.viewModelScope.launch(
            toastingExceptionHandler()
        ) {
            swipeRefresh?.isRefreshing = true
            val result = listModel.search(query)
            swipeRefresh?.isRefreshing = false
            if (result != null && autoplay) {
                autoplay()
            }
        }
    }

    private fun showSearchPrompt() {
        emptyView.findViewById<TextView>(R.id.empty_list_text).setText(R.string.search_prompt)
        emptyView.isVisible = true
    }

    private fun populateList(result: SearchResult) {
        val list = mutableListOf<Identifiable>()

        val artists = result.artists
        if (artists.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_artists))
            list.addAll(artists)
            if (searchResult!!.artists.size > artists.size) {
                list.add(MoreButton(0, ::expandArtists))
            }
        }
        val albums = result.albums
        if (albums.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_albums))
            list.addAll(albums)
            if (searchResult!!.albums.size > albums.size) {
                list.add(MoreButton(1, ::expandAlbums))
            }
        }
        // Music-only product surface: servers may return video entries through search3 even
        // though Video has no visible destination. Do not leak those hidden items back into
        // Search, where tapping them would otherwise launch the legacy video player.
        val songs = result.songs.filterNot { it.isVideo }
        if (songs.isNotEmpty()) {
            list.add(DividerBinder.Divider(R.string.search_songs))
            list.addAll(songs)
            if (searchResult!!.songs.count { !it.isVideo } > songs.size) {
                list.add(MoreButton(2, ::expandSongs))
            }
        }

        // Show/hide the empty text view
        emptyView.findViewById<TextView>(R.id.empty_list_text).setText(R.string.search_no_match)
        emptyView.isVisible = list.isEmpty()

        viewAdapter.submitList(list)
    }

    private fun expandArtists() {
        populateList(listModel.trimResultLength(searchResult!!, maxArtists = Int.MAX_VALUE))
    }

    private fun expandAlbums() {
        populateList(listModel.trimResultLength(searchResult!!, maxAlbums = Int.MAX_VALUE))
    }

    private fun expandSongs() {
        populateList(listModel.trimResultLength(searchResult!!, maxSongs = Int.MAX_VALUE))
    }

    private fun onArtistSelected(item: ArtistOrIndex) {
        // Create action based on type
        val action = if (item is Index) {
            SearchFragmentDirections.searchToTrackCollection(
                id = item.id,
                name = item.name,
                parentId = item.id,
                isArtist = false
            )
        } else {
            SearchFragmentDirections.searchToAlbumsList(
                type = AlbumListType.SORTED_BY_NAME,
                byArtist = true,
                id = item.id,
                title = item.name,
                size = 1000,
                offset = 0
            )
        }

        // Lets go!
        findNavController().navigate(action)
    }

    private fun onAlbumSelected(album: Album, autoplay: Boolean) {
        val action = SearchFragmentDirections.searchToTrackCollection(
            id = album.id,
            name = album.title,
            autoPlay = autoplay,
            isAlbum = true
        )
        findNavController().navigate(action)
    }

    private fun onSongSelected(song: Track, append: Boolean) {
        if (!append) {
            mediaPlayerManager.clear()
        }
        mediaPlayerManager.addToPlaylist(
            listOf(song),
            autoPlay = false,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.APPEND
        )
        mediaPlayerManager.play(mediaPlayerManager.mediaItemCount - 1)
        toast(resources.getQuantityString(R.plurals.n_songs_added_to_end, 1, 1))
    }

    private fun autoplay() {
        val firstSong = searchResult!!.songs.firstOrNull { !it.isVideo }
        if (firstSong != null) {
            onSongSelected(firstSong, false)
        } else if (searchResult!!.albums.isNotEmpty()) {
            onAlbumSelected(searchResult!!.albums[0], true)
        }
    }

    override fun onItemClick(item: Identifiable) {
        when (item) {
            is ArtistOrIndex -> {
                onArtistSelected(item)
            }

            is Track -> onSongSelected(item, true)

            is Album -> {
                onAlbumSelected(item, false)
            }
        }
    }

    @Suppress("LongMethod")
    override fun onContextMenuItemSelected(menuItem: MenuItem, item: Identifiable): Boolean {
        // Here the Item could be a track or an album or an artist
        if (item is Track) {
            return handleContextMenuTracks(
                menuItem = menuItem,
                tracks = listOf(item),
                mediaPlayerManager = mediaPlayerManager,
                fragment = this
            )
        } else {
            return handleContextMenu(
                menuItem = menuItem,
                item = item,
                isArtist = item is Artist,
                mediaPlayerManager = mediaPlayerManager,
                fragment = this
            )
        }
    }
}
