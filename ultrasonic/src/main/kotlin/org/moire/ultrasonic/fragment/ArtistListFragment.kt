/*
 * ArtistListFragment.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.ArtistRowBinder
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.model.ArtistListModel

/**
 * Displays the list of Artists or Indexes (folders) from the media library
 */
class ArtistListFragment : EntryListFragment<ArtistOrIndex>() {

    /**
     * The ViewModel to use to get the data
     */
    override val listModel: ArtistListModel by viewModels()

    /**
     * The id of the main layout
     */
    override val mainLayout = R.layout.list_layout_generic

    private val navArgs: ArtistListFragmentArgs by navArgs()

    /**
     * The central function to pass a query to the model and return a LiveData object
     */
    override fun getLiveData(refresh: Boolean, append: Boolean): LiveData<List<ArtistOrIndex>> =
        listModel.getItems(navArgs.refresh || refresh, swipeRefresh!!)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(navArgs.title)

        viewAdapter.register(
            ArtistRowBinder(
                { entry -> onItemClick(entry) },
                { menuItem, entry -> onContextMenuItemSelected(menuItem, entry) }
            )
        )
    }

    /**
     * There are different targets depending on what list we show.
     * If we are showing indexes, we need to go to TrackCollection
     * If we are showing artists, we need to go to AlbumList
     */
    override fun onItemClick(item: ArtistOrIndex) {
        // Check type
        val action = if (item is Index) {
            NavigationGraphDirections.toTrackCollection(
                id = item.id,
                name = item.name,
                parentId = item.id,
                isArtist = false
            )
        } else {
            NavigationGraphDirections.toAlbumList(
                type = AlbumListType.SORTED_BY_NAME,
                byArtist = true,
                id = item.id,
                title = item.name,
                size = 1000,
                offset = 0
            )
        }

        findNavController().navigate(action)
    }
}
