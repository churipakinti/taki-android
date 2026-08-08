/*
 * DownloadedAlbumFragment.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * A single downloaded album's tracks, opened from the Downloads screen. Built on
 * [TrackCollectionFragment] (isAlbum = true, see navigation_graph.xml) to get the same hero
 * header, disc grouping, selection mode and "⋮" context menu as the normal Album Detail screen
 * -- but sourced entirely from the local database like [DownloadsFragment], so it keeps working
 * with no network at all instead of falling back to a server request for an album the user
 * opened specifically because they wanted to browse it offline.
 */
class DownloadedAlbumFragment : TrackCollectionFragment() {
    override fun getLiveData(
        refresh: Boolean,
        append: Boolean
    ): LiveData<List<MusicDirectory.Child>> {
        val albumId = requireArguments().getString("id")

        if (albumId != null) {
            listModel.viewModelScope.launch(toastingExceptionHandler()) {
                swipeRefresh?.isRefreshing = true
                listModel.getDownloadedAlbumTracks(albumId)
                swipeRefresh?.isRefreshing = false
            }
        }

        return listModel.currentList
    }
}
