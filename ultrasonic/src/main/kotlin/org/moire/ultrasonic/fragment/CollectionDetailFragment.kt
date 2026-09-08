/*
 * CollectionDetailFragment.kt
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.CollectionDetailViewModel
import org.moire.ultrasonic.ui.collection.CollectionDetailActions
import org.moire.ultrasonic.ui.collection.CollectionDetailScreen
import org.moire.ultrasonic.ui.collection.CollectionMember
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Util.toast

/**
 * One Collection / Box Set's member releases (issue #10 phase 4B). A thin Compose host: it
 * owns the nav-graph boundary (the unchanged `collectionDetailFragment` destination and its
 * `grouping` argument) and the list/grid toggle; everything visible - including the lightweight
 * top row (back + toggle + discover) - is [CollectionDetailScreen]. The Activity's Material
 * toolbar is hidden for this destination (see `NavigationActivity.usesContentHeader`), so the
 * grouping name is shown once, in the Compose header.
 *
 * Navigation-only, exactly as the View version was - opening this screen, or scrolling a
 * 222-disc box set, never fetches a track. Tapping a member opens it via the unchanged
 * `TrackCollectionFragment` (now the Compose Album Detail).
 */
class CollectionDetailFragment : Fragment() {

    private val navArgs: CollectionDetailFragmentArgs by navArgs()
    private val viewModel: CollectionDetailViewModel by viewModels()

    /** The list/grid toggle. Not persisted across recreation - COVER on open, the same
     *  default the legacy Fragment used. */
    private val layoutType = MutableStateFlow(LayoutType.COVER)
    private val fallbackChromeInset = MutableStateFlow(0)

    /** "N discs" at the last discovery start, to report how many the crawl added. */
    private var countBeforeDiscovery: Int? = null

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
                    val layout by layoutType.collectAsStateWithLifecycle()
                    val chromeInsetPx by chromeInsetFlow.collectAsStateWithLifecycle()
                    val bottomInset = if (chromeInsetPx > 0) {
                        with(LocalDensity.current) { chromeInsetPx.toDp() }
                    } else {
                        TakiTheme.dimensions.contentInsetFloatingChrome
                    }
                    CollectionDetailScreen(
                        state = state,
                        actions = collectionActions,
                        layout = layout,
                        bottomContentInset = bottomInset,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The Compose header carries the grouping name; the hidden toolbar shows nothing.
        setTitle(this, "")

        viewModel.load(navArgs.grouping)
        observeDiscovery()
    }

    private val collectionActions: CollectionDetailActions by lazy {
        CollectionDetailActions(
            onBack = { findNavController().navigateUp() },
            onToggleLayout = {
                layoutType.value =
                    if (layoutType.value == LayoutType.LIST) LayoutType.COVER else LayoutType.LIST
            },
            onOpenMember = ::openMember,
            onRefresh = viewModel::refresh,
            onDiscoverMore = viewModel::discoverMore,
        )
    }

    private fun openMember(member: CollectionMember) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                member.id,
                isAlbum = true,
                name = member.title,
                parentId = member.parent
            )
        )
    }

    /**
     * The "find missing discs" progress toast + how many it added, kept in the Fragment as UI
     * feedback (the crawl itself is in the ViewModel). Same messages as the legacy
     * `isDiscovering` observer.
     */
    private fun observeDiscovery() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasDiscovering = false
                viewModel.uiState.collect { state ->
                    if (state.isDiscovering && !wasDiscovering) {
                        countBeforeDiscovery = state.discCount
                        toast(R.string.collection_discovering)
                    } else if (!state.isDiscovering && wasDiscovering) {
                        countBeforeDiscovery?.let { before ->
                            val found = state.discCount - before
                            toast(
                                resources.getQuantityString(
                                    R.plurals.collection_discover_result, found, found
                                )
                            )
                        }
                        countBeforeDiscovery = null
                    }
                    wasDiscovering = state.isDiscovering
                }
            }
        }
    }
}
