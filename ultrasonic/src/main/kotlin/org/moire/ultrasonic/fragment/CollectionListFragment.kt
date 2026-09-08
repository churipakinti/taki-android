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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.R
import org.moire.ultrasonic.activity.NavigationActivity
import org.moire.ultrasonic.adapters.CollectionRowAdapter
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.model.CollectionListModel
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Collections/Box Sets list, reached from Library's "Box Sets" row (MainFragment).
 * Deliberately its own small Fragment
 * instead of reusing EntryListFragment<Album>/AlbumListFragment: those are strictly typed to
 * Album across several screens, and MusicCollection doesn't fit that contract - forcing it in
 * would mean widening a shared generic base class for every album list screen, a much bigger and
 * riskier change than this feature needs.
 *
 * Issue #10 phase 4B (visual continuity): the Activity's Material toolbar is hidden for this
 * destination and a lightweight Compose [TakiScreenHeader] (back + "Box Sets") sits on the Taki
 * canvas above the unchanged RecyclerView grid, so this screen looks like the same shell as the
 * screens before (Library) and after (Collection Detail) it. Data, adapter, ordering,
 * navigation and refresh are untouched.
 */
class CollectionListFragment : Fragment() {

    private val listModel: CollectionListModel by viewModels()
    private var emptyView: View? = null
    private var swipeRefresh: SwipeRefreshLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.collection_list_layout, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The Compose header carries the title; the Activity toolbar is hidden for this
        // destination (NavigationActivity.hidesSupportActionBar).
        view.findViewById<ComposeView>(R.id.collection_list_header).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TakiTheme {
                    TakiScreenHeader(
                        onBack = { findNavController().navigateUp() },
                        title = stringResource(R.string.library_box_sets),
                    )
                }
            }
        }

        emptyView = view.findViewById(R.id.empty_list_view)
        view.findViewById<android.widget.TextView>(R.id.empty_list_text)
            ?.setText(R.string.collection_empty)

        val adapter = CollectionRowAdapter(::onItemClick)
        view.findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = GridLayoutManager(context, GRID_SPAN_COUNT)
            this.adapter = adapter
            (activity as? NavigationActivity)?.bindFloatingChromeInset(viewLifecycleOwner, this)
        }

        swipeRefresh = view.findViewById(R.id.swipe_refresh_view)
        swipeRefresh?.setOnRefreshListener {
            listModel.load(refresh = true)
        }

        listModel.collections.observe(viewLifecycleOwner) { collections ->
            adapter.submitList(collections)
            emptyView?.isVisible = collections.isEmpty()
            swipeRefresh?.isRefreshing = false
        }

        listModel.load()
    }

    private fun onItemClick(collection: MusicCollection) {
        findNavController().navigate(
            CollectionListFragmentDirections.toCollectionDetail(collection.title)
        )
    }

    companion object {
        private const val GRID_SPAN_COUNT = 2
    }
}
