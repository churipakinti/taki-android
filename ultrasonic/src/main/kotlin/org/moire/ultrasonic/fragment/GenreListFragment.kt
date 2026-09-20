/*
 * GenreListFragment.kt
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
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.MutableStateFlow
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.GenreListViewModel
import org.moire.ultrasonic.ui.genrelist.GenreListActions
import org.moire.ultrasonic.ui.genrelist.GenreListRow
import org.moire.ultrasonic.ui.genrelist.GenreListScreen
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.Settings.MAX_SONGS

/**
 * Displays the available genres in the media library (issue #10 phase 4G2) - a thin Compose
 * host, following the phase 4E1/4E2/4G1 pattern: it owns the unchanged `selectGenreFragment`
 * nav-graph boundary (no arguments) and the tap-to-navigate command; everything visible is
 * [GenreListScreen]. The Activity's Material toolbar was already hidden for this destination
 * before this phase (`NavigationActivity.hidesSupportActionBar`'s base set already includes
 * `selectGenreFragment`), so the shared `content_navigation_header` supplies the back affordance
 * and no title is ever visibly shown, matching the legacy screen - this Fragment still calls
 * [setTitle] only for parity with the legacy (also invisible) ActionBar title.
 *
 * Unlike Playlists List, there is no RxBus subscription and no dialogs here - the legacy
 * `SelectGenreFragment` had neither (no download status, no context menu, no create/rename/
 * delete).
 */
class GenreListFragment : Fragment() {

    private val viewModel: GenreListViewModel by viewModels()
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
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    GenreListScreen(
                        state = state,
                        actions = genreListActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, getString(R.string.main_genres_title))
        viewModel.load(refresh = false)
    }

    private val genreListActions: GenreListActions by lazy {
        GenreListActions(
            onGenreClick = ::onGenreClick,
            onRefresh = viewModel::refresh,
            onCoverNeeded = viewModel::onCoverNeeded,
        )
    }

    private fun onGenreClick(row: GenreListRow) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                genreName = row.name,
                size = MAX_SONGS,
                offset = 0,
            ),
        )
    }
}
