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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.CollectionRowAdapter
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.CollectionListModel

/**
 * Collections/Box Sets list, reached from Library's "Box Sets" row (MainFragment).
 * Deliberately its own small Fragment
 * instead of reusing EntryListFragment<Album>/AlbumListFragment: those are strictly typed to
 * Album across several screens, and MusicCollection doesn't fit that contract - forcing it in
 * would mean widening a shared generic base class for every album list screen, a much bigger and
 * riskier change than this feature needs.
 */
class CollectionListFragment : Fragment() {

    private val listModel: CollectionListModel by viewModels()
    private var emptyView: View? = null
    private var swipeRefresh: SwipeRefreshLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.list_layout_generic, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, R.string.library_box_sets)

        emptyView = view.findViewById(R.id.empty_list_view)
        view.findViewById<android.widget.TextView>(R.id.empty_list_text)
            ?.setText(R.string.collection_empty)

        val adapter = CollectionRowAdapter(::onItemClick)
        view.findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = GridLayoutManager(context, GRID_SPAN_COUNT)
            this.adapter = adapter
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
