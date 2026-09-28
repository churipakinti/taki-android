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
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.CreatePlaylistViewModel
import org.moire.ultrasonic.ui.createplaylist.CreatePlaylistActions
import org.moire.ultrasonic.ui.createplaylist.CreatePlaylistScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.SortOrder

/**
 * Selects songs and creates a new server playlist only when the selection is saved (issue #10
 * phase 4M2, migrated from the legacy `RecyclerView` + `PlaylistTrackPickerBinder` picker).
 *
 * There is no edit mode - see [CreatePlaylistViewModel]'s own kdoc. Draws no Compose header of
 * its own: this destination relies on the shared Material toolbar exactly like the legacy screen
 * did, so no new `NavigationActivity` chrome flag is needed here.
 */
class CreatePlaylistFragment : Fragment() {

    private val navArgs: CreatePlaylistFragmentArgs by navArgs()
    private val viewModel: CreatePlaylistViewModel by viewModels()

    private var pendingSelection: SortOrder? = null
    private var availableArtists: List<ArtistOrIndex> = emptyList()
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setTitle(this, navArgs.playlistName)

        childFragmentManager.setFragmentResultListener(
            ItemSelectionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle -> handleSelectionDialogResult(bundle) }

        val chromeInsetFlow = (activity as? NavigationActivity)?.contentBottomInset
            ?: fallbackChromeInset
        viewModel.load()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    CreatePlaylistScreen(
                        state = state,
                        actions = actions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    private val actions: CreatePlaylistActions by lazy {
        CreatePlaylistActions(
            onSearchQueryChange = viewModel::onQueryChange,
            onSearchSubmit = ::runSearch,
            onSearchClear = viewModel::onClear,
            onSortOrderSelected = ::onSortOrderSelected,
            onTrackToggle = { row -> viewModel.toggleTrack(row.id) },
            onLoadMore = viewModel::loadMore,
            onSave = ::savePlaylist,
        )
    }

    private fun runSearch() {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            viewModel.onSearchSubmit()
        }
    }

    private fun onSortOrderSelected(order: SortOrder) {
        when (order) {
            SortOrder.BY_ARTIST -> showArtistSelection()
            SortOrder.BY_GENRE -> showGenreSelection()
            else -> viewModel.onSortOrderSelected(order)
        }
    }

    private fun showArtistSelection() {
        pendingSelection = SortOrder.BY_ARTIST
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            availableArtists = viewModel.loadArtists().sortedBy {
                it.name.orEmpty().lowercase(Locale.ROOT)
            }
            showSelectionDialog(
                R.string.main_artists_title,
                availableArtists.mapNotNull { it.name }.toTypedArray()
            )
        }
    }

    private fun showGenreSelection() {
        pendingSelection = SortOrder.BY_GENRE
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val genres = viewModel.loadGenres().map { it.name }.sorted().toTypedArray()
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

    private fun handleSelectionDialogResult(bundle: Bundle) {
        if (bundle.getBoolean(ItemSelectionDialogFragment.RESULT_CANCELLED)) {
            pendingSelection = null
            return
        }
        val selected = bundle.getString(ItemSelectionDialogFragment.RESULT_SELECTED_ITEM)
            ?: return
        when (pendingSelection) {
            SortOrder.BY_ARTIST -> {
                val artist = availableArtists.firstOrNull { it.name == selected } ?: return
                viewModel.selectArtist(artist.id, selected)
            }
            SortOrder.BY_GENRE -> viewModel.selectGenre(selected)
            else -> Unit
        }
        pendingSelection = null
    }

    private fun savePlaylist() {
        val tracks = viewModel.selectedTracksSnapshot()
        if (tracks == null) {
            toast(R.string.playlist_select_song)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(
            toastingExceptionHandler(getString(R.string.playlist_create_error))
        ) {
            viewModel.save(navArgs.playlistName, tracks)
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                PLAYLIST_CREATED_RESULT,
                true
            )
            toast(R.string.playlist_created)
            findNavController().popBackStack()
        }
    }

    companion object {
        const val PLAYLIST_CREATED_RESULT = "playlist_created_result"
    }
}
