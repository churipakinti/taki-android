/*
 * ArtistListFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ArtistGridBinder
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.model.ArtistListModel
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.FilterButtonBar
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities

/**
 * Displays the list of Artists or Indexes (folders) from the media library
 */
class ArtistListFragment(private var layoutType: LayoutType = LayoutType.COVER) :
    EntryListFragment<ArtistOrIndex>(),
    FilterableFragment {

    override val listModel: ArtistListModel by viewModels()
    override val mainLayout = R.layout.list_layout_generic

    // Same fix as AlbumListFragment (commit 026aa795, "don't refresh the album list on back
    // navigation"): without this, every open of this screen defaults to refresh=true and
    // bypasses the Room cache backing getArtists()/getIndexes() unconditionally. This screen's
    // own nav argument already defaults refresh to false; it's this Kotlin-level default that
    // was still forcing it on.
    override val refreshOnCreation: Boolean = false

    private val navArgs: ArtistListFragmentArgs by navArgs()
    private var filterButtonBar: FilterButtonBar? = null
    private var orderType: SortOrder = SortOrder.BY_NAME

    // Set only when setOrderType() changes the order (never on initial load or when the same
    // order is re-applied, e.g. state restored on back navigation) - see onListCommitted().
    private var resetScrollOnNextUpdate = false

    override var viewCapabilities = ViewCapabilities(
        supportsGrid = true,
        supportedSortOrders = getListOfSortOrders(),
        sortOrderLabels = mapOf(SortOrder.BY_NAME to R.string.main_artists_alphaByName)
    )

    private val isStandalone: Boolean
        get() = parentFragment !is MainFragment

    override fun getLiveData(refresh: Boolean, append: Boolean): LiveData<List<ArtistOrIndex>> {
        listModel.setSortOrder(orderType)
        return listModel.getItems(navArgs.refresh || refresh, swipeRefresh!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            savedInstanceState.getString(LAYOUT_TYPE_KEY)?.let {
                layoutType = LayoutType.valueOf(it)
            }
            savedInstanceState.getString(ORDER_TYPE_KEY)?.let {
                orderType = SortOrder.valueOf(it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = if (isStandalone) R.layout.list_layout_filterable else mainLayout
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(navArgs.title ?: getString(R.string.main_artists_title))

        val onClick = { entry: ArtistOrIndex -> onItemClick(entry) }
        val onMenuClick = { menuItem: android.view.MenuItem, entry: ArtistOrIndex ->
            onContextMenuItemSelected(menuItem, entry)
        }

        viewAdapter.register(ArtistOrIndex::class).to(
            ArtistRowBinder(
                onItemClick = onClick,
                onContextMenuClick = onMenuClick,
                enableSections = false,
                alwaysShowPicture = true,
                defaultPicture = R.drawable.artist_placeholder
            ),
            ArtistGridBinder(onClick, onMenuClick)
        ).withKotlinClassLinker { _, _ ->
            when (layoutType) {
                LayoutType.LIST -> ArtistRowBinder::class
                LayoutType.COVER -> ArtistGridBinder::class
            }
        }

        if (isStandalone) setupFilterBar(view)
        setLayoutType(layoutType)
        setOrderType(orderType)
    }

    override fun setLayoutType(newType: LayoutType) {
        layoutType = newType
        viewManager = when (newType) {
            LayoutType.LIST -> LinearLayoutManager(context)

            LayoutType.COVER -> GridLayoutManager(context, ARTIST_GRID_COLUMNS).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (viewAdapter.items.getOrNull(position) is ArtistOrIndex) {
                            1
                        } else {
                            ARTIST_GRID_COLUMNS
                        }
                }
            }
        }
        listView?.layoutManager = viewManager
        viewAdapter.notifyDataSetChanged()
    }

    override fun setOrderType(newOrder: SortOrder) {
        // Re-sorting the same dataset by a different criterion invalidates the current scroll
        // position entirely (the item that was on screen means nothing in the new order), so
        // the list must land back at the top - but only when the order actually changes, not on
        // initial load or when the previous order is merely re-applied (e.g. restored on back
        // navigation), where the existing scroll position is still meaningful and must survive.
        if (newOrder != orderType) resetScrollOnNextUpdate = true
        orderType = newOrder
        listModel.setSortOrder(newOrder, swipeRefresh)
    }

    override fun getOrderType(): SortOrder = orderType

    override fun onListCommitted() {
        if (resetScrollOnNextUpdate) {
            resetScrollOnNextUpdate = false
            listView?.scrollToPosition(0)
        }
    }

    private fun setupFilterBar(view: View) {
        filterButtonBar = view.findViewById(R.id.filter_button_bar)
        filterButtonBar?.setOnLayoutTypeChangedListener(::setLayoutType)
        filterButtonBar?.setOnOrderChangedListener(::setOrderType)
        filterButtonBar?.configureWithCapabilities(viewCapabilities, orderType)
        filterButtonBar?.setLayoutType(layoutType)
    }

    // Kept deliberately small: Name, Recently Played, Recently Added, Most Played (in that
    // order). Random/Starred/By Genre are gone; Albums/Songs keep their own full option sets
    // (getListOfSortOrders is local to this fragment, not shared). The online/offline gating
    // mirrors what each order actually needs:
    // RECENT/FREQUENT require a live server, NEWEST/BY_NAME can also work from an ID3-tagged
    // offline cache.
    private fun getListOfSortOrders(): List<SortOrder> {
        val useId3Offline = Settings.id3TagsEnabledOffline
        val isOnline = !ActiveServerProvider.isOffline()
        val supported = mutableListOf<SortOrder>()

        if (isOnline || useId3Offline) supported.add(SortOrder.BY_NAME)
        if (isOnline) supported.add(SortOrder.RECENT)
        if (isOnline || useId3Offline) supported.add(SortOrder.NEWEST)
        if (isOnline) supported.add(SortOrder.FREQUENT)

        return supported
    }

    override fun onItemClick(item: ArtistOrIndex) {
        val action = if (item is Index) {
            NavigationGraphDirections.toTrackCollection(
                id = item.id,
                name = item.name,
                parentId = item.id,
                isArtist = false
            )
        } else {
            NavigationGraphDirections.toArtistDetail(
                artistId = item.id,
                artistName = item.name ?: getString(R.string.common_artist),
                artistCoverArt = item.coverArt
            )
        }

        findNavController().navigate(action)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(LAYOUT_TYPE_KEY, layoutType.name)
        outState.putString(ORDER_TYPE_KEY, orderType.name)
    }

    companion object {
        private const val ARTIST_GRID_COLUMNS = 3
        private const val LAYOUT_TYPE_KEY = "artist_layout_type"
        private const val ORDER_TYPE_KEY = "artist_order_type"
    }
}
