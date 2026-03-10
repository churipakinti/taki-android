/*
 * TrackCollectionModel.kt
 * Copyright (C) 2009-2021 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.Util

/*
* Model for retrieving different collections of tracks from the API
*/
class TrackCollectionModel(application: Application) : GenericListModel(application) {

    val currentList: MutableLiveData<List<MusicDirectory.Child>> = MutableLiveData()
    private var loadedUntil: Int = 0

    /*
     * Especially when dealing with indexes, this method can return Albums, Entries or a mix of both!
     */
    suspend fun getMusicDirectory(refresh: Boolean, id: String, name: String?) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getMusicDirectory(id, name, refresh)
            currentListIsSortable = true
            updateList(musicDirectory)
        }
    }

    suspend fun getAlbum(refresh: Boolean, id: String, name: String?) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory: MusicDirectory = service.getAlbumAsDir(id, name, refresh)
            currentListIsSortable = true
            updateList(musicDirectory)
        }
    }

    suspend fun getSongsForGenre(genre: String, count: Int, offset: Int, append: Boolean) {
        // Handle the logic for endless scrolling:
        // If appending the existing list, set the offset from where to load
        var newOffset = offset
        if (append) newOffset += (count + loadedUntil)

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getSongsByGenre(genre, count, newOffset)
            currentListIsSortable = false
            updateList(musicDirectory, append)

            // Update current offset
            loadedUntil = newOffset
        }
    }

    suspend fun getStarred() {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory: MusicDirectory

            musicDirectory = if (ActiveServerProvider.shouldUseId3Tags()) {
                Util.getSongsFromSearchResult(service.getStarred2())
            } else {
                Util.getSongsFromSearchResult(service.getStarred())
            }
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    suspend fun getVideos(refresh: Boolean) {
        showHeader = false

        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val videos = service.getVideos(refresh)
            if (videos != null) {
                currentListIsSortable = false
                updateList(videos)
            }
        }
    }

    suspend fun getRandom(size: Int, append: Boolean) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getRandomSongs(size)
            currentListIsSortable = false
            updateList(musicDirectory, append)
        }
    }

    suspend fun getPlaylist(playlistId: String, playlistName: String) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPlaylist(playlistId, playlistName)
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    suspend fun getPodcastEpisodes(podcastChannelId: String) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = service.getPodcastEpisodes(podcastChannelId)
            if (musicDirectory != null) {
                currentListIsSortable = false
                updateList(musicDirectory)
            }
        }
    }

    suspend fun getShare(shareId: String) {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = MusicDirectory()

            val shares = service.getShares(true)

            for (share in shares) {
                if (share.id == shareId) {
                    for (entry in share.getEntries()) {
                        musicDirectory.add(entry)
                    }
                    break
                }
            }
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    suspend fun getBookmarks() {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val musicDirectory = Util.getSongsFromBookmarks(service.getBookmarks())
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    private fun updateList(root: MusicDirectory, append: Boolean = false) {
        val newList = if (append) {
            currentList.value!! + root.getChildren()
        } else {
            root.getChildren()
        }

        currentList.postValue(newList)
    }

    @Synchronized
    fun calculateButtonState(selection: List<Track>, onComplete: (ButtonStates) -> Unit) {
        val enabled = selection.isNotEmpty()
        var unpinEnabled = false
        var deleteEnabled = false
        var downloadEnabled = false
        var pinnedCount = 0

        viewModelScope.launch(Dispatchers.IO) {
            for (song in selection) {
                when (DownloadService.getDownloadState(song)) {
                    DownloadState.DONE -> {
                        deleteEnabled = true
                    }

                    DownloadState.PINNED -> {
                        deleteEnabled = true
                        pinnedCount++
                        unpinEnabled = true
                    }

                    DownloadState.IDLE, DownloadState.FAILED -> {
                        downloadEnabled = true
                    }

                    else -> {}
                }
            }
        }.invokeOnCompletion {
            val pinEnabled = selection.size > pinnedCount

            onComplete(
                ButtonStates(
                    all = enabled,
                    pin = pinEnabled,
                    unpin = unpinEnabled,
                    delete = deleteEnabled,
                    download = downloadEnabled
                )
            )
        }
    }

    companion object {
        data class ButtonStates(
            val all: Boolean,
            val pin: Boolean,
            val unpin: Boolean,
            val delete: Boolean,
            val download: Boolean
        )
    }
}
