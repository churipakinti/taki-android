/*
 * AlbumListFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.AlbumGridDelegate
import org.moire.ultrasonic.adapters.AlbumRowDelegate
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.model.AlbumListModel
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.FilterButtonBar
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities

/**
 * Displays a list of Albums from the media library
 */
class AlbumListFragment(
    private var layoutType: LayoutType = LayoutType.LIST,
    private var orderType: SortOrder? = null
) : EntryListFragment<Album>(),
    FilterableFragment {

    private var filterButtonBar: FilterButtonBar? = null

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: AlbumListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout: Int = R.layout.list_layout_generic

    /**
     * Whether to refresh the data onViewCreated
     */
    override val refreshOnCreation: Boolean = false

    private val navArgs: AlbumListFragmentArgs by navArgs()

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(refresh: Boolean, append: Boolean): LiveData<List<Album>> {
        fetchAlbums(refresh)

        return listModel.list
    }

    private fun fetchAlbums(refresh: Boolean = navArgs.refresh, append: Boolean = navArgs.append) {
        listModel.viewModelScope.launch(
            toastingExceptionHandler()
        ) {
            swipeRefresh?.isRefreshing = true

            if (navArgs.byArtist) {
                listModel.getAlbumsOfArtist(
                    refresh = refresh,
                    id = navArgs.id!!,
                    name = navArgs.title
                )
            } else {
                listModel.getAlbums(
                    albumListType = orderType?.mapToAlbumListType() ?: navArgs.type,
                    size = navArgs.size,
                    offset = navArgs.offset,
                    append = append,
                    refresh = refresh or append
                )
            }
            swipeRefresh?.isRefreshing = false
        }
    }

    override fun setLayoutType(newType: LayoutType) {
        layoutType = newType
        viewManager = if (layoutType == LayoutType.LIST) {
            LinearLayoutManager(this.context)
        } else {
            GridLayoutManager(this.context, ROWS)
        }

        listView!!.layoutManager = viewManager

        // Attach our onScrollListener
        val scrollListener = object : EndlessScrollListener(viewManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                // Triggered only when new data needs to be appended to the list
                // Add whatever code is needed to append new items to the bottom of the list
                fetchAlbums(append = true)
            }
        }

        listView!!.addOnScrollListener(scrollListener)
    }

    override fun setOrderType(newOrder: SortOrder) {
        orderType = newOrder

        // If we are on an Artist page we just need to reorder the list. Otherwise refetch
        if (navArgs.byArtist) {
            listModel.sortListByOrder(newOrder.mapToAlbumListType())
        } else {
            fetchAlbums(refresh = true, append = false)
        }
    }

    override var viewCapabilities: ViewCapabilities = ViewCapabilities(
        supportsGrid = true,
        supportedSortOrders = getListOfSortOrders()
    )

    private fun getListOfSortOrders(): List<SortOrder> {
        val useId3 = Settings.id3TagsEnabledOnline
        val useId3Offline = Settings.id3TagsEnabledOffline
        val isOnline = !ActiveServerProvider.isOffline()

        val supported = mutableListOf<SortOrder>()

        if (isOnline || useId3Offline) {
            supported.add(SortOrder.NEWEST)
        }
        if (isOnline) {
            supported.add(SortOrder.RECENT)
        }
        if (isOnline) {
            supported.add(SortOrder.FREQUENT)
        }
        if (isOnline && !useId3) {
            supported.add(SortOrder.HIGHEST)
        }
        if (isOnline) {
            supported.add(SortOrder.RANDOM)
        }
        if (isOnline) {
            supported.add(SortOrder.STARRED)
        }
        if (isOnline || useId3Offline) {
            supported.add(SortOrder.BY_NAME)
        }
        if (isOnline || useId3Offline) {
            supported.add(SortOrder.BY_ARTIST)
        }

        return supported
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = if (navArgs.byArtist) R.layout.list_layout_filterable else mainLayout
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup refresh handler
        swipeRefresh = view.findViewById(refreshListId)
        swipeRefresh?.setOnRefreshListener {
            fetchAlbums(refresh = true)
        }

        // In most cases this fragment will be hosted by a ViewPager2 in the MainFragment,
        // which provides its own FilterBar.
        // But when we are looking at the Albums of a specific Artist this Fragment is standalone,
        // so we need to setup the FilterBar here..
        if (navArgs.byArtist) {
            setTitle(navArgs.title)
            setupFilterBar(view)
        }

        // Get a reference to the listView
        listView = view.findViewById(recyclerViewId)

        setLayoutType(layoutType)

        // Magic to switch between different view layouts:
        // We register two delegates, one which layouts grid items and one which layouts row items
        // Based on the current status of the ViewType, the right delegate is picked.
        viewAdapter.register(Album::class).to(
            AlbumRowDelegate(::onItemClick, ::onContextMenuItemSelected),
            AlbumGridDelegate(::onItemClick, ::onContextMenuItemSelected)
        ).withKotlinClassLinker { _, _ ->
            when (layoutType) {
                LayoutType.COVER -> AlbumGridDelegate::class
                LayoutType.LIST -> AlbumRowDelegate::class
            }
        }

        emptyTextView.setText(R.string.select_album_empty)
    }

    private fun setupFilterBar(view: View) {
        // Load last layout from settings
        layoutType = LayoutType.from(Settings.lastViewType)
        filterButtonBar = view.findViewById(R.id.filter_button_bar)
        filterButtonBar!!.setOnLayoutTypeChangedListener(::setLayoutType)
        filterButtonBar!!.setOnOrderChangedListener(::setOrderType)
        filterButtonBar!!.configureWithCapabilities(
            ViewCapabilities(
                supportsGrid = true,
                supportedSortOrders = listOf(
                    SortOrder.BY_NAME,
                    SortOrder.BY_YEAR
                )
            )
        )

        // Set layout toggle Chip to correct state
        filterButtonBar!!.setLayoutType(layoutType)
    }

    override fun onItemClick(item: Album) {
        val action = NavigationGraphDirections.toTrackCollection(
            item.id,
            isAlbum = item.isDirectory,
            name = item.title,
            parentId = item.parent
        )
        findNavController().navigate(action)
    }

    private fun SortOrder.mapToAlbumListType(): AlbumListType = when (this) {
        SortOrder.RANDOM -> AlbumListType.RANDOM
        SortOrder.NEWEST -> AlbumListType.NEWEST
        SortOrder.HIGHEST -> AlbumListType.HIGHEST
        SortOrder.FREQUENT -> AlbumListType.FREQUENT
        SortOrder.RECENT -> AlbumListType.RECENT
        SortOrder.BY_NAME -> AlbumListType.SORTED_BY_NAME
        SortOrder.BY_ARTIST -> AlbumListType.SORTED_BY_ARTIST
        SortOrder.STARRED -> AlbumListType.STARRED
        SortOrder.BY_YEAR -> AlbumListType.BY_YEAR
    }

    companion object {
        private const val ROWS = 3
    }
}
