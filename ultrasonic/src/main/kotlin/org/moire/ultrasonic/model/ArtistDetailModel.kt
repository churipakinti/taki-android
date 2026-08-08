/*
 * ArtistDetailModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.ArtistInfo
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory

/** Loads the real server data used by the dedicated artist detail screen. */
class ArtistDetailModel :
    ViewModel(),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    val albums: MutableLiveData<List<Album>> = MutableLiveData(emptyList())
    val tracks: MutableLiveData<List<Track>> = MutableLiveData(emptyList())
    val artistInfo: MutableLiveData<ArtistInfo?> = MutableLiveData(null)
    val loaded: MutableLiveData<Boolean> = MutableLiveData(false)

    suspend fun load(artistId: String, artistName: String, refresh: Boolean) = coroutineScope {
        loaded.value = false
        val service = MusicServiceFactory.getMusicService()
        val albumsRequest = async(Dispatchers.IO) {
            service.getAlbumsOfArtist(artistId, artistName, refresh)
        }
        val artistInfoRequest = async(Dispatchers.IO) {
            runCatching { service.getArtistInfo(artistId) }.getOrNull()
        }
        val topSongsRequest = async(Dispatchers.IO) {
            runCatching {
                service.getTopSongs(artistName, TRACK_FETCH_SIZE)
            }.getOrDefault(emptyList())
        }

        val artistAlbums = albumsRequest.await().sortedWith(
            compareByDescending<Album> { it.year ?: 0 }
                .thenBy { it.title.orEmpty().lowercase() }
        )
        val artistTracks = topSongsRequest.await()
            .filter { track ->
                track.artistId == artistId || track.artist.equals(artistName, ignoreCase = true)
            }
            .distinctBy { it.id }
            .ifEmpty {
                withContext(Dispatchers.IO) {
                    fetchArtistTracks(service, artistId, artistName)
                }
            }.ifEmpty {
            fetchTracksFromFirstAlbums(service, artistAlbums)
        }

        albums.value = artistAlbums
        tracks.value = artistTracks
        artistInfo.value = artistInfoRequest.await()
        loaded.value = true
    }

    private fun fetchArtistTracks(
        service: MusicService,
        artistId: String,
        artistName: String
    ): List<Track> {
        val criteria = SearchCriteria(
            query = artistName,
            artistCount = 0,
            albumCount = 0,
            songCount = TRACK_FETCH_SIZE,
            musicFolderId = activeServerProvider.getActiveServer().musicFolderId,
            artistId = artistId
        )

        return service.search(criteria)?.songs.orEmpty()
            .filter { track ->
                track.artistId == artistId || track.artist.equals(artistName, ignoreCase = true)
            }
            .distinctBy { it.id }
    }

    private suspend fun fetchTracksFromFirstAlbums(
        service: MusicService,
        albums: List<Album>
    ): List<Track> = withContext(Dispatchers.IO) {
        albums.take(FALLBACK_ALBUM_COUNT).flatMap { album ->
            service.getAlbumAsDir(album.id, album.title, false).getTracks()
        }.distinctBy { it.id }
    }

    companion object {
        private const val TRACK_FETCH_SIZE = 100
        private const val FALLBACK_ALBUM_COUNT = 3
    }
}
