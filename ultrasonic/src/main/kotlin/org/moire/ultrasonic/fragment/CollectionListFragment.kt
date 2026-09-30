/*
 * CollectionListFragment.kt
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
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.model.CollectionListViewModel
import org.moire.ultrasonic.ui.collectionlist.CollectionListActions
import org.moire.ultrasonic.ui.collectionlist.CollectionListRow
import org.moire.ultrasonic.ui.collectionlist.CollectionListScreen
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Collections/Box Sets list, reached from Library's "Box Sets" row (MainFragment). Deliberately
 * its own small Fragment instead of reusing EntryListFragment<Album>/AlbumListFragment: those
 * are strictly typed to Album across several screens, and MusicCollection doesn't fit that
 * contract.
 *
 * Post-issue-#10 residual migration (phase 5A1): now a thin Compose host, the same shape as
 * [CollectionDetailFragment] - it threads the live floating-chrome inset into
 * [CollectionListScreen] and owns navigation to Collection Detail. Data, ordering and refresh
 * semantics are unchanged from the legacy `CollectionListModel`.
 */
class CollectionListFragment : Fragment() {

    private val viewModel: CollectionListViewModel by viewModels()
    private val fallbackChromeInset = MutableStateFlow(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val chromeInsetFlow =
            (activity as? NavigationActivity)?.contentBottomInset ?: fallbackChromeInset
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
                    CollectionListScreen(
                        state = state,
                        actions = collectionListActions,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.load()
    }

    private val collectionListActions: CollectionListActions by lazy {
        CollectionListActions(
            onBack = { findNavController().navigateUp() },
            onCollectionClick = ::onItemClick,
            onRefresh = viewModel::refresh,
        )
    }

    private fun onItemClick(row: CollectionListRow) {
        findNavController().navigate(
            CollectionListFragmentDirections.toCollectionDetail(row.title)
        )
    }
}
