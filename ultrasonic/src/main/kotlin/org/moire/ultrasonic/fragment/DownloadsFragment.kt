/*
 * DownloadsFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.DownloadedAlbumRowBinder
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.model.TrackCollectionModel
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * A download manager: the albums that currently have downloaded/pinned tracks, shown as cards
 * (same visual language as the Playlists screen). Tapping a card opens [DownloadedAlbumFragment]
 * for that album; the trash icon removes its downloaded tracks directly from this screen without
 * having to open it first.
 *
 * Deliberately does not extend [TrackCollectionFragment] -- this screen shows Album cards, not
 * Track rows, so it has nothing to gain from that class's track-selection/context-menu
 * machinery. [MultiListFragment] already provides everything actually needed (swipe refresh,
 * empty state, RecyclerView).
 */
class DownloadsFragment : MultiListFragment<Album>() {
    override val listModel: TrackCollectionModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setTitle(this, R.string.menu_downloads)
        emptyTextView.setText(R.string.download_empty)

        viewAdapter.register(
            DownloadedAlbumRowBinder(
                onItemClick = ::onItemClick,
                onRemoveDownload = ::removeAlbumDownload
            )
        )
    }

    override fun getLiveData(refresh: Boolean, append: Boolean): LiveData<List<Album>> {
        listModel.viewModelScope.launch(toastingExceptionHandler()) {
            swipeRefresh?.isRefreshing = true
            listModel.getDownloadedAlbums()
            swipeRefresh?.isRefreshing = false
        }
        return listModel.downloadedAlbums
    }

    override fun onItemClick(item: Album) {
        findNavController().navigate(
            NavigationGraphDirections.toDownloadedAlbum(
                id = item.id,
                name = item.title
            )
        )
    }

    private fun removeAlbumDownload(album: Album) {
        viewLifecycleOwner.lifecycleScope.launch(toastingExceptionHandler()) {
            val tracks = listModel.getDownloadedTracksForAlbum(album.id)
            DownloadService.deleteAsync(tracks)
            listModel.getDownloadedAlbums()

            Toast.makeText(
                requireContext(),
                resources.getQuantityString(R.plurals.n_songs_deleted, tracks.size, tracks.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onContextMenuItemSelected(menuItem: MenuItem, item: Album): Boolean = false
}
