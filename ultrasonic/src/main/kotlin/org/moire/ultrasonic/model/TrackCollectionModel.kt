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
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchCriteria
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
    private var allSongsNextOffset: Int = 0
    private var allSongsLoading: Boolean = false
    var canLoadMoreAllSongs: Boolean = true
        private set
    private var artistSongsNextOffset: Int = 0
    private var artistSongsLoading: Boolean = false
    var canLoadMoreArtistSongs: Boolean = true
        private set

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

    suspend fun getAllSongs(count: Int, offset: Int, append: Boolean) {
        if (allSongsLoading || (append && !canLoadMoreAllSongs)) return
        allSongsLoading = true

        try {
            if (!append) {
                allSongsNextOffset = offset
                canLoadMoreAllSongs = true
            }

            withContext(Dispatchers.IO) {
                val service = MusicServiceFactory.getMusicService()
                val criteria = SearchCriteria(
                    query = "",
                    artistCount = 0,
                    albumCount = 0,
                    songCount = count,
                    songOffset = allSongsNextOffset,
                    musicFolderId = activeServer.musicFolderId
                )
                val songs = service.search(criteria)?.songs.orEmpty()
                val musicDirectory = MusicDirectory().apply { addAll(songs) }

                currentListIsSortable = false
                updateList(musicDirectory, append)
                allSongsNextOffset += songs.size
                canLoadMoreAllSongs = songs.size == count
            }
        } finally {
            allSongsLoading = false
        }
    }

    suspend fun getSongsForArtist(
        artistId: String,
        artistName: String,
        count: Int,
        append: Boolean
    ) {
        if (artistSongsLoading || (append && !canLoadMoreArtistSongs)) return
        artistSongsLoading = true

        try {
            if (!append) {
                artistSongsNextOffset = 0
                canLoadMoreArtistSongs = true
            }

            withContext(Dispatchers.IO) {
                val service = MusicServiceFactory.getMusicService()
                val criteria = SearchCriteria(
                    query = artistName,
                    artistCount = 0,
                    albumCount = 0,
                    songCount = count,
                    songOffset = artistSongsNextOffset,
                    musicFolderId = activeServer.musicFolderId,
                    artistId = artistId
                )
                val searchSongs = service.search(criteria)?.songs.orEmpty()
                val exactSongs = searchSongs.filter { track ->
                    track.artistId == artistId || track.artist.equals(artistName, ignoreCase = true)
                }
                val musicDirectory = MusicDirectory().apply { addAll(exactSongs) }

                currentListIsSortable = false
                updateList(musicDirectory, append)
                artistSongsNextOffset += searchSongs.size
                canLoadMoreArtistSongs = searchSongs.size == count
            }
        } finally {
            artistSongsLoading = false
        }
    }

    suspend fun getArtists(refresh: Boolean): List<ArtistOrIndex> =
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            if (ActiveServerProvider.shouldUseId3Tags()) {
                service.getArtists(refresh)
            } else {
                service.getIndexes(activeServer.musicFolderId, refresh)
            }
        }

    suspend fun getGenres(refresh: Boolean): List<Genre> = withContext(Dispatchers.IO) {
        MusicServiceFactory.getMusicService().getGenres(refresh)
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
            // Prevent duplicates.
            (currentList.value.orEmpty() + root.getChildren()).distinctBy { it.id }
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
