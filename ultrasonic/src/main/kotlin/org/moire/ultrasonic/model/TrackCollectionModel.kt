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
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.AlbumInfo
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DailyMixQueueBuilder
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.Util

/*
* Model for retrieving different collections of tracks from the API
*/
class TrackCollectionModel(application: Application) : GenericListModel(application) {

    val currentList: MutableLiveData<List<MusicDirectory.Child>> = MutableLiveData()

    /**
     * The Downloads screen's data source. [DownloadService.observableDownloads] only reflects
     * the active download queue (currently downloading/queued) -- once a download finishes it
     * drops out of that list entirely, which is why tracks downloaded in a previous session
     * never showed up here. The actual persistent record of "what's downloaded" is the same
     * local metadata database offline browsing already reads from (populated when a download
     * completes, pruned by [org.moire.ultrasonic.util.CacheCleaner] on delete).
     */
    suspend fun getDownloadedTracks() {
        withContext(Dispatchers.IO) {
            val tracks = activeServerProvider.offlineMetaDatabase.trackDao().get()
                .sortedBy { it.title.orEmpty().lowercase(Locale.ROOT) }
            val musicDirectory = MusicDirectory().apply { addAll(tracks) }
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    val downloadedAlbums: MutableLiveData<List<Album>> = MutableLiveData()

    /**
     * The Downloads screen's top level: downloaded tracks grouped by album. An album's
     * songCount reflects its full length on the server, which may be larger than what's
     * actually downloaded (the user might have grabbed only some tracks) -- so it's overwritten
     * here with the real local count instead of trusting the cached server value.
     */
    suspend fun getDownloadedAlbums() {
        withContext(Dispatchers.IO) {
            val db = activeServerProvider.offlineMetaDatabase
            val counts = db.trackDao().get().groupingBy { it.albumId }.eachCount()
            val albums = db.albumDao().get()
                .onEach { it.songCount = (counts[it.id] ?: 0).toLong() }
                .sortedBy { it.title.orEmpty().lowercase(Locale.ROOT) }
            downloadedAlbums.postValue(albums)
        }
    }

    /**
     * A single downloaded album's tracks, for [org.moire.ultrasonic.fragment.DownloadedAlbumFragment]
     * -- reuses the same local-only data path as [getDownloadedTracks] so this screen works
     * with no network at all, unlike the normal Album Detail flow.
     */
    suspend fun getDownloadedAlbumTracks(albumId: String) {
        withContext(Dispatchers.IO) {
            val tracks = getDownloadedTracksForAlbum(albumId)
                .sortedWith(EntryByDiscAndTrackComparator())
            val musicDirectory = MusicDirectory().apply { addAll(tracks) }
            currentListIsSortable = false
            updateList(musicDirectory)
        }
    }

    suspend fun getDownloadedTracksForAlbum(albumId: String): List<Track> =
        withContext(Dispatchers.IO) {
            activeServerProvider.offlineMetaDatabase.trackDao().byAlbum(albumId)
        }

    private var genreSongsNextOffset: Int = 0
    private var genreSongsLoading: Boolean = false
    var canLoadMoreGenreSongs: Boolean = true
        private set
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

    /**
     * Optional enrichment (album notes/review from the server's external metadata agent).
     * Not every server/album has it, so failures or missing data resolve to null rather than
     * surfacing an error -- the track list itself doesn't depend on this.
     */
    private var loadedAlbumInfoId: String? = null
    private var loadedAlbumInfo: AlbumInfo? = null

    /**
     * getAlbumInfo() has no caching of its own in CachedMusicService (unlike the track listing
     * itself), and this ViewModel survives Fragment recreation (e.g. rotation) while the
     * Fragment's own fields don't -- without this, rotating the device re-fetched the "About"
     * text from the network every time even though the album on screen never changed.
     */
    suspend fun getAlbumInfo(id: String, forceRefresh: Boolean = false): AlbumInfo? {
        if (!forceRefresh && loadedAlbumInfoId == id) return loadedAlbumInfo

        val info = withContext(Dispatchers.IO) {
            try {
                MusicServiceFactory.getMusicService().getAlbumInfo(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                null
            }
        }
        loadedAlbumInfoId = id
        loadedAlbumInfo = info
        return info
    }

    suspend fun getSongsForGenre(genre: String, count: Int, offset: Int, append: Boolean) {
        if (genreSongsLoading || (append && !canLoadMoreGenreSongs)) return
        genreSongsLoading = true

        try {
            if (!append) {
                genreSongsNextOffset = offset
                canLoadMoreGenreSongs = true
            }

            withContext(Dispatchers.IO) {
                val service = MusicServiceFactory.getMusicService()
                val musicDirectory = service.getSongsByGenre(genre, count, genreSongsNextOffset)
                val songCount = musicDirectory.getChildren().size
                currentListIsSortable = false
                updateList(musicDirectory, append)

                genreSongsNextOffset += songCount
                canLoadMoreGenreSongs = songCount == count
            }
        } finally {
            genreSongsLoading = false
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

    suspend fun getArtists(refresh: Boolean): List<ArtistOrIndex> = withContext(Dispatchers.IO) {
        val service = MusicServiceFactory.getMusicService()
        if (ActiveServerProvider.shouldUseId3Tags()) {
            service.getArtists(refresh)
        } else {
            // null (server confirmed nothing changed) never actually reaches here: this always
            // goes through CachedMusicService, which resolves that internally and only ever
            // returns its own already-cached list. See MusicService.getIndexes.
            service.getIndexes(activeServer.musicFolderId, refresh) ?: emptyList()
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

    suspend fun getDailyMix() {
        withContext(Dispatchers.IO) {
            val tracks = DailyMixQueueBuilder(MusicServiceFactory.getMusicService()).build()
            val musicDirectory = MusicDirectory().apply { addAll(tracks) }
            currentListIsSortable = false
            showHeader = false
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
        var deleteEnabled = false
        var downloadEnabled = false

        viewModelScope.launch(Dispatchers.IO) {
            for (song in selection) {
                when (DownloadService.getDownloadState(song)) {
                    DownloadState.DONE, DownloadState.PINNED -> {
                        deleteEnabled = true
                    }

                    DownloadState.IDLE, DownloadState.FAILED -> {
                        downloadEnabled = true
                    }

                    else -> {}
                }
            }
        }.invokeOnCompletion {
            onComplete(
                ButtonStates(
                    all = enabled,
                    delete = deleteEnabled,
                    download = downloadEnabled
                )
            )
        }
    }

    companion object {
        data class ButtonStates(
            val all: Boolean,
            val delete: Boolean,
            val download: Boolean
        )
    }
}
