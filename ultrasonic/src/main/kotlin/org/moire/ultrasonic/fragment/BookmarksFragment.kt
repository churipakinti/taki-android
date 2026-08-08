/*
 * BookmarksFragment.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.moire.ultrasonic.R
import org.moire.ultrasonic.adapters.BaseAdapter
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.fragment.FragmentTitle.setTitle
import org.moire.ultrasonic.service.MediaPlayerManager
import org.moire.ultrasonic.util.toastingExceptionHandler

/**
 * Lists the Bookmarks available on the server
 *
 * Bookmarks allows to save the play position of tracks, especially useful for longer tracks like
 * audio books etc.
 *
 * Therefore this fragment allows only for singular selection and playback.
 */
class BookmarksFragment : TrackCollectionFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setTitle(this, R.string.button_bar_bookmarks)

        viewAdapter.selectionType = BaseAdapter.SelectionType.SINGLE
    }

    override fun getLiveData(
        refresh: Boolean,
        append: Boolean
    ): LiveData<List<MusicDirectory.Child>> {
        listModel.viewModelScope.launch(
            toastingExceptionHandler()
        ) {
            swipeRefresh?.isRefreshing = true
            listModel.getBookmarks()
            swipeRefresh?.isRefreshing = false
        }
        return listModel.currentList
    }

    /**
     * Tapping a bookmark resumes playback from its saved position, instead of the base
     * class's default "play from here" (which would always start at position 0).
     */
    override fun onItemClick(item: MusicDirectory.Child) {
        if (item is Track) {
            playNow(listOf(item))
        } else {
            super.onItemClick(item)
        }
    }

    /**
     * Play the selected tracks at the bookmarked position
     */
    private fun playNow(songs: List<Track>) {
        if (songs.isNotEmpty()) {
            mediaPlayerManager.addToPlaylist(
                songs = songs,
                autoPlay = false,
                shuffle = false,
                insertionMode = MediaPlayerManager.InsertionMode.CLEAR
            )

            mediaPlayerManager.seekTo(0, songs[0].bookmarkPosition)
            mediaPlayerManager.prepare()
            mediaPlayerManager.play()
        }
    }
}
