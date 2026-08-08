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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
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
import org.moire.ultrasonic.util.toastingExceptionHandler
import org.moire.ultrasonic.view.FilterButtonBar
import org.moire.ultrasonic.view.SortOrder
import org.moire.ultrasonic.view.ViewCapabilities

/**
 * Displays the list of Artists or Indexes (folders) from the media library
 */
class ArtistListFragment(
    private var layoutType: LayoutType = LayoutType.COVER
) : EntryListFragment<ArtistOrIndex>(), FilterableFragment {

    override val listModel: ArtistListModel by viewModels()
    override val mainLayout = R.layout.list_layout_generic

    private val navArgs: ArtistListFragmentArgs by navArgs()
    private var filterButtonBar: FilterButtonBar? = null
    private var orderType: SortOrder = SortOrder.NEWEST
    private var selectedGenre: String? = null

    override var viewCapabilities = ViewCapabilities(
        supportsGrid = true,
        supportedSortOrders = getListOfSortOrders()
    )

    private val isStandalone: Boolean
        get() = parentFragment !is MainFragment

    override fun getLiveData(
        refresh: Boolean,
        append: Boolean
    ): LiveData<List<ArtistOrIndex>> {
        listModel.setSortOrder(orderType, genre = selectedGenre)
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
            selectedGenre = savedInstanceState.getString(SELECTED_GENRE_KEY)
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

        childFragmentManager.setFragmentResultListener(
            ItemSelectionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(ItemSelectionDialogFragment.RESULT_CANCELLED)) {
                swipeRefresh?.isRefreshing = false
                return@setFragmentResultListener
            }

            bundle.getString(ItemSelectionDialogFragment.RESULT_SELECTED_ITEM)?.let { genre ->
                selectedGenre = genre
                listModel.setSortOrder(SortOrder.BY_GENRE, swipeRefresh, genre)
            }
        }

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
                        if (viewAdapter.items.getOrNull(position) is ArtistOrIndex) 1
                        else ARTIST_GRID_COLUMNS
                }
            }
        }
        listView?.layoutManager = viewManager
        viewAdapter.notifyDataSetChanged()
    }

    override fun setOrderType(newOrder: SortOrder) {
        orderType = newOrder
        if (newOrder == SortOrder.BY_GENRE) {
            val genre = selectedGenre
            if (genre == null) showGenreSelection()
            else listModel.setSortOrder(newOrder, swipeRefresh, genre)
        } else {
            selectedGenre = null
            listModel.setSortOrder(newOrder, swipeRefresh)
        }
    }

    override fun getOrderType(): SortOrder = orderType

    private fun setupFilterBar(view: View) {
        filterButtonBar = view.findViewById(R.id.filter_button_bar)
        filterButtonBar?.setOnLayoutTypeChangedListener(::setLayoutType)
        filterButtonBar?.setOnOrderChangedListener(::setOrderType)
        filterButtonBar?.configureWithCapabilities(viewCapabilities, orderType)
        filterButtonBar?.setLayoutType(layoutType)
    }

    private fun showGenreSelection() {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            swipeRefresh?.isRefreshing = true
            val genres = try {
                listModel.getGenres(true)
            } finally {
                swipeRefresh?.isRefreshing = false
            }

            if (genres.isEmpty()) return@launch
            val genreNames = genres.map { it.name }.toTypedArray()
            if (childFragmentManager.findFragmentByTag(ItemSelectionDialogFragment.TAG) == null) {
                ItemSelectionDialogFragment.create(R.string.main_genres_title, genreNames)
                    .show(childFragmentManager, ItemSelectionDialogFragment.TAG)
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun getListOfSortOrders(): List<SortOrder> {
        val useId3 = Settings.id3TagsEnabledOnline
        val useId3Offline = Settings.id3TagsEnabledOffline
        val isOnline = !ActiveServerProvider.isOffline()
        val supported = mutableListOf<SortOrder>()

        if (isOnline || useId3Offline) supported.add(SortOrder.NEWEST)
        if (isOnline) supported.add(SortOrder.RECENT)
        if (isOnline) supported.add(SortOrder.FREQUENT)
        if (isOnline && !useId3) supported.add(SortOrder.HIGHEST)
        if (isOnline) supported.add(SortOrder.RANDOM)
        if (isOnline) supported.add(SortOrder.STARRED)
        if (isOnline || useId3Offline) supported.add(SortOrder.BY_NAME)
        if (isOnline || useId3Offline) supported.add(SortOrder.BY_GENRE)

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
        outState.putString(SELECTED_GENRE_KEY, selectedGenre)
    }

    companion object {
        private const val ARTIST_GRID_COLUMNS = 3
        private const val LAYOUT_TYPE_KEY = "artist_layout_type"
        private const val ORDER_TYPE_KEY = "artist_order_type"
        private const val SELECTED_GENRE_KEY = "artist_selected_genre"
    }
}
