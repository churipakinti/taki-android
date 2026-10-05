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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import org.moire.ultrasonic.ui.components.TakiPickerOption
import org.moire.ultrasonic.ui.components.TakiPickerSheet
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

    /** The open artist/genre picker sheet (issue #10 phase 5A6, replacing the legacy
     *  ItemSelectionDialogFragment), `null` = closed. Items are display names; the selection is
     *  resolved by index, so duplicate names cannot collide. */
    private data class Picker(val sortOrder: SortOrder, val titleRes: Int, val items: List<String>)

    private val picker = mutableStateOf<Picker?>(null)
    private var availableArtists: List<ArtistOrIndex> = emptyList()
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setTitle(this, navArgs.playlistName)

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
                    Box(Modifier.fillMaxSize()) {
                        CreatePlaylistScreen(
                            state = state,
                            actions = actions,
                            bottomContentInset = bottomInset,
                        )
                        val open = picker.value
                        if (open != null) {
                            TakiPickerSheet(
                                title = stringResource(open.titleRes),
                                options = remember(open) {
                                    open.items.mapIndexed { i, name -> TakiPickerOption(i.toString(), name) }
                                },
                                onSelect = { key -> onPicked(open, key.toInt()) },
                                onDismiss = { picker.value = null },
                                dismissLabel = stringResource(R.string.common_cancel),
                                bottomContentInset = bottomInset,
                                sheetTestTag = CREATE_PLAYLIST_PICKER_SHEET_TEST_TAG,
                            )
                        }
                    }
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
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            availableArtists = viewModel.loadArtists().sortedBy {
                it.name.orEmpty().lowercase(Locale.ROOT)
            }
            showSelection(
                SortOrder.BY_ARTIST,
                R.string.main_artists_title,
                availableArtists.mapNotNull { it.name }
            )
        }
    }

    private fun showGenreSelection() {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val genres = viewModel.loadGenres().map { it.name }.sorted()
            showSelection(SortOrder.BY_GENRE, R.string.main_genres_title, genres)
        }
    }

    /** Opens the picker sheet; like the legacy dialog never stacked, never empty. */
    private fun showSelection(sortOrder: SortOrder, title: Int, items: List<String>) {
        if (items.isEmpty() || picker.value != null) return
        picker.value = Picker(sortOrder, title, items)
    }

    private fun onPicked(open: Picker, index: Int) {
        picker.value = null
        val selected = open.items.getOrNull(index) ?: return
        when (open.sortOrder) {
            SortOrder.BY_ARTIST -> {
                val artist = availableArtists.firstOrNull { it.name == selected } ?: return
                viewModel.selectArtist(artist.id, selected)
            }
            SortOrder.BY_GENRE -> viewModel.selectGenre(selected)
            else -> Unit
        }
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

        /** Lets tests and the Pixel validation find the artist/genre picker. */
        const val CREATE_PLAYLIST_PICKER_SHEET_TEST_TAG = "create_playlist_picker_sheet"
    }
}
