/*
 * CollectionDetailFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.CollectionDiscAdapter
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.CollectionDetailModel
import org.moire.ultrasonic.subsonic.ImageLoaderProvider
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Util.toast
import org.moire.ultrasonic.util.bindStackedArtwork

/**
 * One Collection/Box Set's discs. Only ever works with lightweight Album rows already in
 * view - opening this screen, or scrolling through 222 of
 * them, never fetches a single track. Navigation-only: tapping a disc opens it via the existing,
 * unmodified TrackCollectionFragment (Album Detail), where Play/queue actions already live - this
 * screen doesn't duplicate them.
 */
class CollectionDetailFragment : Fragment(), KoinComponent {

    private val navArgs: CollectionDetailFragmentArgs by navArgs()
    private val listModel: CollectionDetailModel by viewModels()
    private val imageLoaderProvider: ImageLoaderProvider by inject()
    private var emptyView: View? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var discoverMenuItem: MenuItem? = null
    private var toggleLayoutMenuItem: MenuItem? = null
    private var recyclerView: RecyclerView? = null
    private var adapter: CollectionDiscAdapter? = null
    private var headerArtwork: View? = null
    private var headerTitle: TextView? = null
    private var headerSubtitle: TextView? = null
    private var layoutType = LayoutType.COVER

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.collection_detail, menu)
            discoverMenuItem = menu.findItem(R.id.menu_discover_more_discs)
            toggleLayoutMenuItem = menu.findItem(R.id.menu_toggle_collection_layout)
            updateToggleIcon()
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.menu_discover_more_discs -> {
                listModel.discoverMore(navArgs.grouping)
                true
            }
            R.id.menu_toggle_collection_layout -> {
                setLayoutType(
                    if (layoutType == LayoutType.LIST) LayoutType.COVER else LayoutType.LIST
                )
                true
            }
            else -> false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.collection_detail_layout, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(this, navArgs.grouping)

        (requireActivity() as MenuHost).addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )

        emptyView = view.findViewById(R.id.empty_list_view)
        headerArtwork = view.findViewById(R.id.collection_header_artwork)
        headerTitle = view.findViewById(R.id.collection_header_title)
        headerSubtitle = view.findViewById(R.id.collection_header_subtitle)
        headerTitle?.text = navArgs.grouping

        adapter = CollectionDiscAdapter(onOpen = ::openDisc)
        recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view).apply {
            adapter = this@CollectionDetailFragment.adapter
        }
        setLayoutType(layoutType)

        swipeRefresh = view.findViewById(R.id.swipe_refresh_view)
        swipeRefresh?.setOnRefreshListener {
            listModel.load(navArgs.grouping, refresh = true)
        }

        listModel.collection.observe(viewLifecycleOwner) { collection ->
            val albums = collection?.albums.orEmpty()
            adapter?.submitList(albums)
            emptyView?.isVisible = albums.isEmpty()
            swipeRefresh?.isRefreshing = false
            updateHeader(collection)
        }

        var previousCount: Int? = null
        listModel.isDiscovering.observe(viewLifecycleOwner) { discovering ->
            discoverMenuItem?.isEnabled = !discovering
            if (discovering) {
                previousCount = listModel.collection.value?.albumCount
                toast(R.string.collection_discovering)
            } else if (previousCount != null) {
                val found = (listModel.collection.value?.albumCount ?: 0) - previousCount!!
                toast(
                    resources.getQuantityString(R.plurals.collection_discover_result, found, found)
                )
                previousCount = null
            }
        }

        listModel.load(navArgs.grouping)
    }

    private fun updateHeader(collection: MusicCollection?) {
        val artwork = headerArtwork ?: return
        val albumCount = collection?.albumCount ?: 0
        headerTitle?.text = collection?.title ?: navArgs.grouping
        headerSubtitle?.text = resources.getQuantityString(
            R.plurals.n_discs,
            albumCount,
            albumCount
        )
        bindStackedArtwork(artwork, collection?.stackArtwork.orEmpty(), imageLoaderProvider)
    }

    private fun setLayoutType(newType: LayoutType) {
        layoutType = newType
        adapter?.layoutType = newType
        recyclerView?.layoutManager = if (newType == LayoutType.LIST) {
            LinearLayoutManager(context)
        } else {
            GridLayoutManager(context, GRID_SPAN_COUNT)
        }
        // notifyDataSetChanged(), not just a layoutManager swap: the two layouts use different
        // item view types (see CollectionDiscAdapter.getItemViewType), so existing view holders
        // must be discarded, not rebound in place.
        adapter?.notifyDataSetChanged()
        updateToggleIcon()
    }

    private fun updateToggleIcon() {
        toggleLayoutMenuItem?.setIcon(
            if (layoutType == LayoutType.LIST) {
                R.drawable.ic_baseline_view_grid
            } else {
                R.drawable.ic_baseline_view_list
            }
        )
    }

    private fun openDisc(disc: Album) {
        findNavController().navigate(
            NavigationGraphDirections.toTrackCollection(
                disc.id,
                isAlbum = true,
                name = disc.title,
                parentId = disc.parent
            )
        )
    }

    companion object {
        private const val GRID_SPAN_COUNT = 2
    }
}
