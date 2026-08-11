/*
 * ArtistListModel.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */
package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.CommunicationError
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder

/**
 * Provides ViewModel which contains the list of available Artists
 */
class ArtistListModel(application: Application) : GenericListModel(application) {
    private val artists: MutableLiveData<List<ArtistOrIndex>> = MutableLiveData()
    private var allArtists: List<ArtistOrIndex> = emptyList()
    private var sortOrder: SortOrder = SortOrder.NEWEST
    private var selectedGenre: String? = null
    private var sortJob: Job? = null

    /**
     * Retrieves all available Artists in a LiveData
     */
    fun getItems(refresh: Boolean, swipe: SwipeRefreshLayout): LiveData<List<ArtistOrIndex>> {
        // Don't reload the data if navigating back to the view that was active before.
        // This way, we keep the scroll position
        if (artists.value?.isEmpty() != false || refresh) {
            backgroundLoadFromServer(refresh, swipe)
        }
        return artists
    }

    override fun load(
        isOffline: Boolean,
        useId3Tags: Boolean,
        musicService: MusicService,
        refresh: Boolean
    ) {
        super.load(isOffline, useId3Tags, musicService, refresh)

        val musicFolderId = activeServer.musicFolderId

        val result = if (ActiveServerProvider.shouldUseId3Tags()) {
            musicService.getArtists(refresh)
        } else {
            // null means the server confirmed nothing changed (see MusicService.getIndexes) --
            // this always goes through CachedMusicService, which never surfaces that as null
            // to its own callers, but the interface itself is nullable.
            musicService.getIndexes(musicFolderId, refresh) ?: allArtists
        }

        allArtists = result
        artists.postValue(filterAndSortItems(result, musicService))
    }

    /**
     * Applies the same collection order used by the album screen to the artist list.
     * Album-backed orders show the artists represented by that album collection.
     */
    fun setSortOrder(order: SortOrder, swipe: SwipeRefreshLayout? = null, genre: String? = null) {
        sortOrder = order
        selectedGenre = genre

        val source = allArtists
        if (source.isEmpty()) return

        sortJob?.cancel()
        sortJob = viewModelScope.launch {
            swipe?.isRefreshing = true
            try {
                val result = withContext(Dispatchers.IO) {
                    filterAndSortItems(source, MusicServiceFactory.getMusicService())
                }
                artists.value = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CommunicationError.handleError(error, context)
            } finally {
                swipe?.isRefreshing = false
            }
        }
    }

    suspend fun getGenres(refresh: Boolean): List<Genre> = withContext(Dispatchers.IO) {
        MusicServiceFactory.getMusicService().getGenres(refresh)
    }

    private fun filterAndSortItems(
        items: List<ArtistOrIndex>,
        musicService: MusicService
    ): List<ArtistOrIndex> = when (sortOrder) {
        SortOrder.ALL_SONGS,
        SortOrder.BY_NAME,
        SortOrder.BY_ARTIST -> items.sortedWith(comparator)

        SortOrder.RANDOM -> items.shuffled()

        SortOrder.STARRED -> getStarredArtists(items, musicService)

        SortOrder.BY_GENRE -> {
            val genre = selectedGenre
            if (genre == null) {
                items.sortedWith(comparator)
            } else {
                artistsFromAlbums(items, getAlbums(musicService, AlbumListType.BY_GENRE, genre))
            }
        }

        SortOrder.NEWEST,
        SortOrder.RECENT,
        SortOrder.FREQUENT,
        SortOrder.HIGHEST -> artistsFromAlbums(
            items,
            getAlbums(musicService, sortOrder.toAlbumListType())
        )

        // The artist filter does not expose this option, because artists do not have a year.
        SortOrder.BY_YEAR -> items.sortedWith(comparator)
    }

    private fun getAlbums(
        musicService: MusicService,
        albumListType: AlbumListType,
        genre: String? = null
    ): List<Album> {
        val musicFolderId = activeServer.musicFolderId
        return if (genre != null || ActiveServerProvider.shouldUseId3Tags()) {
            musicService.getAlbumList2(
                albumListType,
                Settings.MAX_ALBUMS,
                0,
                genre,
                musicFolderId
            )
        } else {
            musicService.getAlbumList(
                albumListType,
                Settings.MAX_ALBUMS,
                0,
                musicFolderId
            )
        }
    }

    private fun getStarredArtists(
        items: List<ArtistOrIndex>,
        musicService: MusicService
    ): List<ArtistOrIndex> {
        val starred = if (ActiveServerProvider.shouldUseId3Tags()) {
            musicService.getStarred2()
        } else {
            musicService.getStarred()
        }

        val directMatches = matchArtists(items, starred.artists)
        val albumMatches = artistsFromAlbums(items, starred.albums)
        return (directMatches + albumMatches).distinctBy { it.id }
    }

    private fun artistsFromAlbums(
        items: List<ArtistOrIndex>,
        albums: List<Album>
    ): List<ArtistOrIndex> {
        val byId = items.associateBy { it.id }
        val byName = items.associateBy { normalizeName(it.name) }

        return albums.mapNotNull { album ->
            album.artistId?.let(byId::get) ?: byName[normalizeName(album.artist)]
        }.distinctBy { it.id }
    }

    private fun matchArtists(
        items: List<ArtistOrIndex>,
        candidates: List<ArtistOrIndex>
    ): List<ArtistOrIndex> {
        val byId = items.associateBy { it.id }
        val byName = items.associateBy { normalizeName(it.name) }
        return candidates.mapNotNull { candidate ->
            byId[candidate.id] ?: byName[normalizeName(candidate.name)]
        }.distinctBy { it.id }
    }

    private fun normalizeName(name: String?): String = name.orEmpty().trim().lowercase(Locale.ROOT)

    private fun SortOrder.toAlbumListType(): AlbumListType = when (this) {
        SortOrder.NEWEST -> AlbumListType.NEWEST
        SortOrder.RECENT -> AlbumListType.RECENT
        SortOrder.FREQUENT -> AlbumListType.FREQUENT
        SortOrder.HIGHEST -> AlbumListType.HIGHEST
        else -> error("Unsupported album-backed artist order: $this")
    }

    override fun showSelectFolderHeader(): Boolean = true

    companion object {
        val comparator: Comparator<ArtistOrIndex> =
            compareBy(Collator.getInstance()) { t -> t.name }
    }
}
