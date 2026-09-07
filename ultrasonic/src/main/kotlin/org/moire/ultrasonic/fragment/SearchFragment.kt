/*
 * SearchFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.model.SearchViewModel
import org.moire.ultrasonic.model.SearchViewModel.AutoplayTarget
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.ui.search.SearchActions
import org.moire.ultrasonic.ui.search.SearchAlbumUi
import org.moire.ultrasonic.ui.search.SearchArtistUi
import org.moire.ultrasonic.ui.search.SearchScreen
import org.moire.ultrasonic.ui.search.SearchSongUi
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.Util

/**
 * Thin Compose host for the Search screen (issue #10 phase 3). Owns nothing of the UI: it
 * collects [SearchViewModel.uiState], renders [SearchScreen] under [TakiTheme], and forwards
 * navigation (via the `NavController`) and the song play command (via [MediaPlayerManager])
 * behind [SearchActions]. The `searchFragment` destination id, its graph actions/args,
 * back stack, and the Activity's "hide floating chrome while the search IME is up" behaviour
 * are unchanged; the Compose screen consumes the shared
 * `NavigationActivity.contentBottomInset` (no shell logic here).
 */
class SearchFragment : Fragment() {

    private val searchViewModel: SearchViewModel by viewModels()
    private val mediaPlayerManager: MediaPlayerManager by inject()
    private val navArgs by navArgs<SearchFragmentArgs>()

    // Fallback until the Activity has computed its first inset (mirrors Home / Library).
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val chromeInsetFlow = (activity as? NavigationActivity)?.contentBottomInset
            ?: fallbackChromeInset
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    val state by searchViewModel.uiState.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    SearchScreen(
                        state = state,
                        actions = searchActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.search_title)
        setupImeBackHandling(view)

        // ACTION_SEARCH / voice query (the Activity already persisted it as a recent search).
        val initialQuery = navArgs.query
        if (initialQuery != null && searchViewModel.uiState.value.query.isBlank()) {
            searchViewModel.setInitialQuery(initialQuery)
            if (navArgs.autoplay) {
                viewLifecycleOwner.lifecycleScope.launch {
                    searchViewModel.uiState.first { it.submitted && !it.isSearching }
                    autoplay()
                }
            }
        }
    }

    override fun onDestroyView() {
        Util.hideKeyboard(activity)
        super.onDestroyView()
    }

    // Verbatim from the legacy screen: a visible IME swallows the first Back and hides itself
    // before back navigation is allowed to run.
    private fun setupImeBackHandling(view: View) {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val insets = ViewCompat.getRootWindowInsets(view)
                    if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                        WindowInsetsControllerCompat(requireActivity().window, view)
                            .hide(WindowInsetsCompat.Type.ime())
                        return
                    }
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )
    }

    private val searchActions: SearchActions by lazy {
        SearchActions(
            onQueryChange = searchViewModel::onQueryChange,
            onSubmit = searchViewModel::onSubmit,
            onClearQuery = searchViewModel::onClearQuery,
            onRecentSearchTap = searchViewModel::onRecentSearchTap,
            onRemoveRecentSearch = searchViewModel::onRemoveRecentSearch,
            onClearAllRecentSearches = {
                searchViewModel.onClearAllRecentSearches()
                Util.toast(R.string.search_history_cleared, context = requireContext())
            },
            onShowMoreArtists = searchViewModel::onShowMoreArtists,
            onShowMoreAlbums = searchViewModel::onShowMoreAlbums,
            onShowMoreSongs = searchViewModel::onShowMoreSongs,
            onArtistClick = ::openArtist,
            onAlbumClick = ::openAlbum,
            onSongClick = ::playSong,
        )
    }

    private fun openArtist(artist: SearchArtistUi) {
        onResultOpened()
        val action = if (artist.isIndex) {
            SearchFragmentDirections.searchToTrackCollection(
                id = artist.id,
                name = artist.name,
                parentId = artist.id,
                isArtist = false,
            )
        } else {
            SearchFragmentDirections.searchToAlbumsList(
                type = AlbumListType.SORTED_BY_NAME,
                byArtist = true,
                id = artist.id,
                title = artist.name,
                size = ARTIST_ALBUMS_PAGE_SIZE,
                offset = 0,
            )
        }
        findNavController().navigate(action)
    }

    private fun openAlbum(album: SearchAlbumUi) {
        onResultOpened()
        findNavController().navigate(
            SearchFragmentDirections.searchToTrackCollection(
                id = album.id,
                name = album.title,
                isAlbum = true,
            ),
        )
    }

    /**
     * Tapping a song in Search replaces the queue with just that song, exactly as the legacy
     * screen did (`InsertionMode.CLEAR`, no autoplay flag). Play Next / Play Last stay on the
     * long-press context menu on the still-View screens.
     */
    private fun playSong(song: SearchSongUi) {
        onResultOpened()
        val track = searchViewModel.trackFor(song.id) ?: return
        mediaPlayerManager.addToPlaylist(
            songs = listOf(track),
            autoPlay = false,
            shuffle = false,
            insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
            startIndex = 0,
        )
    }

    private fun autoplay() {
        when (val target = searchViewModel.autoplayTarget()) {
            is AutoplayTarget.PlaySong -> {
                mediaPlayerManager.addToPlaylist(
                    songs = listOf(target.track),
                    autoPlay = true,
                    shuffle = false,
                    insertionMode = MediaPlayerManager.InsertionMode.CLEAR,
                    startIndex = 0,
                )
            }
            is AutoplayTarget.OpenAlbum -> {
                findNavController().navigate(
                    SearchFragmentDirections.searchToTrackCollection(
                        id = target.id,
                        name = target.name,
                        autoPlay = true,
                        isAlbum = true,
                    ),
                )
            }
            null -> Unit
        }
    }

    private fun onResultOpened() {
        Util.hideKeyboard(activity)
        searchViewModel.onResultOpened()
    }

    private companion object {
        private const val ARTIST_ALBUMS_PAGE_SIZE = 1000
    }
}
