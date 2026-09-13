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
 * [TrackCollectionFragment] (isAlbum = true, see navigation_graph.xml) to get the same Compose
 * Album Detail screen, hero header, disc grouping and "⋮" context menu as the normal Album
 * Detail screen (issue #10 phase 4D) -- but sourced entirely from the local database like
 * [DownloadsFragment], so it keeps working with no network at all instead of falling back to a
 * server request for an album the user opened specifically because they wanted to browse it
 * offline. [isDownloadedAlbumSource] tells [org.moire.ultrasonic.model.AlbumDetailViewModel] to
 * use its local-database loader instead of the network one, and to skip the notes/starred
 * network fold-in, same as this class's own (now legacy-path-only) [getLiveData] always did.
 */
class DownloadedAlbumFragment : TrackCollectionFragment() {

    // Phase 4D: this fragment now renders through the same Compose Album Detail screen as a
    // normal album (allowComposeAlbumDetail defaults to true) - isDownloadedAlbumSource is what
    // actually routes its data to the local database. getLiveData below stays correct as a
    // fallback for the legacy View path, which only runs if allowComposeAlbumDetail is ever
    // overridden false again.
    override val isDownloadedAlbumSource: Boolean = true

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
