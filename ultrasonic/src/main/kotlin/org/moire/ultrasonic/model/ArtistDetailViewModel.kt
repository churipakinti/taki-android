/*
 * ArtistDetailViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.imageloader.artistArtRequestOrNull
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.artist.ARTIST_POPULAR_TRACK_COUNT
import org.moire.ultrasonic.ui.artist.ArtistAlbumUi
import org.moire.ultrasonic.ui.artist.ArtistDetailArgs
import org.moire.ultrasonic.ui.artist.ArtistDetailUiState
import org.moire.ultrasonic.ui.artist.ArtistSimilarUi
import org.moire.ultrasonic.ui.artist.ArtistTrackUi
import org.moire.ultrasonic.util.Util

/**
 * Owns the Compose Artist Detail state (issue #10 phase 4C) as one
 * [StateFlow]<[ArtistDetailUiState]>. A projection of exactly what the legacy
 * `ArtistDetailModel` loaded: the year-desc album sort, the top-songs -> search ->
 * first-album track fallback chain, the HTML-stripped biography, the similar-artists list, and
 * the issue #16 cover-art gap-fill (resolve the artist's own id from `getArtists` when the
 * caller navigated here without one).
 *
 * Playback, radio, download and navigation stay in the Fragment; this class only reads. The
 * state survives Fragment recreation (retained ViewModel), so returning from an album / a
 * similar artist restores the screen with no new server call.
 */
class ArtistDetailViewModel :
    ViewModel(),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedArtistId: String? = null

    /** The unsliced fetched track list - the Fragment's play-from-row reads it (legacy
     *  `playTrack` queued `model.tracks.value`, the full list, not just the 5 shown). */
    private var rawTracks: List<Track> = emptyList()

    /** Builds the Coil model for the hero from an artist name + cover-art id, using the same
     *  artist-name cache key the View screen used. A seam so unit tests (which have no
     *  `Storage` root) can stub it. */
    internal var heroArtwork: (artistName: String?, coverArtId: String?) -> CoverArtRequest? =
        { name, id -> artistArtRequestOrNull(name, id, large = true) }

    /** Everything Artist Detail needs, fetched in one place. Default: the same four parallel
     *  server calls the legacy model made, plus the top-songs -> search -> first-album track
     *  fallback chain. Test seam. */
    internal var dataLoader: suspend (ArtistDetailArgs) -> ArtistData = { args ->
        coroutineScope {
            val service = MusicServiceFactory.getMusicService()

            val coverArtRequest = async(Dispatchers.IO) {
                if (!args.knownCoverArt.isNullOrBlank()) {
                    null
                } else {
                    runCatching {
                        service.getArtists(refresh = false)
                            .firstOrNull { it.id == args.artistId }
                            ?.coverArt
                    }.getOrNull()
                }
            }
            val albumsRequest = async(Dispatchers.IO) {
                service.getAlbumsOfArtist(args.artistId, args.artistName, args.refresh)
            }
            val artistInfoRequest = async(Dispatchers.IO) {
                runCatching { service.getArtistInfo(args.artistId) }.getOrNull()
            }
            val topSongsRequest = async(Dispatchers.IO) {
                runCatching { service.getTopSongs(args.artistName, TRACK_FETCH_SIZE) }
                    .getOrDefault(emptyList())
            }

            val artistAlbums = albumsRequest.await()
            val artistTracks = topSongsRequest.await()
                .filterForArtist(args)
                .ifEmpty { withContext(Dispatchers.IO) { searchForArtistTracks(service, args) } }
                .ifEmpty { fetchTracksFromFirstAlbums(service, artistAlbums) }
            val info = artistInfoRequest.await()

            ArtistData(
                albums = artistAlbums,
                tracks = artistTracks,
                biography = info?.biography,
                similarArtists = info?.similarArtists.orEmpty(),
                resolvedCoverArt = args.knownCoverArt?.takeIf { it.isNotBlank() }
                    ?: coverArtRequest.await()?.takeIf { it.isNotBlank() },
            )
        }
    }

    /**
     * Load the artist once. Idempotent across Fragment view recreation - the same artist
     * (barring an explicit refresh) returns without a new call, exactly like the legacy
     * `if (!refresh && loadedArtistId == artistId && loaded.value == true) return`.
     */
    fun load(args: ArtistDetailArgs) {
        val normalized = args.copy(refresh = false)
        // loadedArtistId is only set after a successful fetch, so this is the legacy
        // `loadedArtistId == artistId && loaded == true` guard (empty artists included).
        if (loadedArtistId == args.artistId && !args.refresh) return
        loadedArtistId = null

        _uiState.update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                artistId = args.artistId,
                artistName = args.artistName.ifEmpty { it.artistName },
                artworkModel = it.artworkModel ?: heroArtwork(args.artistName, args.knownCoverArt),
            )
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val data = try {
                dataLoader(normalized.copy(refresh = args.refresh))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            if (data == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = !it.hasContent) }
                return@launch
            }

            rawTracks = data.tracks
            _uiState.update { project(it, args, data) }
            loadedArtistId = args.artistId
        }
    }

    /** Pull-to-refresh: re-fetch through [load], keeping the current sections on screen. A
     *  refresh while one is already running is ignored. */
    fun refresh() {
        if (_uiState.value.isLoading) return
        val id = loadedArtistId ?: return
        val current = _uiState.value
        load(
            ArtistDetailArgs(
                artistId = id,
                artistName = current.artistName,
                knownCoverArt = null,
                refresh = true,
            ),
        )
    }

    /** All fetched artist tracks - the Fragment's play-from-row command reads this. */
    fun tracksSnapshot(): List<Track> = rawTracks

    fun trackFor(id: String): Track? = rawTracks.firstOrNull { it.id == id }

    private fun project(
        current: ArtistDetailUiState,
        args: ArtistDetailArgs,
        data: ArtistData,
    ): ArtistDetailUiState {
        val sortedAlbums = data.albums.sortedWith(
            compareByDescending<Album> { it.year ?: 0 }
                .thenBy { it.title.orEmpty().lowercase(Locale.ROOT) },
        )
        val biography = data.biography
            ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
            ?.takeIf { it.isNotEmpty() }

        return current.copy(
            isLoading = false,
            loadFailed = false,
            artistId = args.artistId,
            artistName = args.artistName.ifEmpty { current.artistName },
            artworkModel = heroArtwork(args.artistName, data.resolvedCoverArt)
                ?: current.artworkModel,
            albumCount = sortedAlbums.size,
            biography = biography,
            albums = sortedAlbums.map { it.toUi() }.toImmutableList(),
            popularTracks = data.tracks.take(ARTIST_POPULAR_TRACK_COUNT)
                .mapIndexed { index, track -> track.toUi(index + 1) }
                .toImmutableList(),
            similarArtists = data.similarArtists.map { it.toUi() }.toImmutableList(),
        )
    }

    private fun Album.toUi() = ArtistAlbumUi(
        id = id,
        parent = parent,
        title = title.orEmpty(),
        subtitle = artist.orEmpty(),
        artworkModel = coverArtRequestOrNull(large = false),
    )

    private fun Track.toUi(rank: Int) = ArtistTrackUi(
        id = id,
        rank = rank.toString(),
        title = (title ?: name).orEmpty(),
        subtitle = album?.takeIf { it.isNotBlank() },
        duration = duration?.takeIf { it > 0 }?.let { Util.formatTotalDuration(it.toLong()) },
        isVideo = isVideo,
    )

    private fun Artist.toUi() = ArtistSimilarUi(
        id = id,
        name = name.orEmpty(),
        coverArt = coverArt,
        artworkModel = artistArtRequestOrNull(name, coverArt, large = false),
    )

    private fun List<Track>.filterForArtist(args: ArtistDetailArgs): List<Track> = this
        .filter {
            it.artistId == args.artistId || it.artist.equals(args.artistName, ignoreCase = true)
        }
        .distinctBy { it.id }

    /** Legacy `fetchArtistTracks`: a `search` fallback when top-songs came back empty. A
     *  failure here must not abort the load. */
    private suspend fun searchForArtistTracks(
        service: MusicService,
        args: ArtistDetailArgs,
    ): List<Track> = try {
        val criteria = SearchCriteria(
            query = args.artistName,
            artistCount = 0,
            albumCount = 0,
            songCount = TRACK_FETCH_SIZE,
            musicFolderId = activeServerProvider.getActiveServer().musicFolderId,
            artistId = args.artistId,
        )
        service.search(criteria)?.songs.orEmpty().filterForArtist(args)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") error: Exception) {
        emptyList()
    }

    /** Legacy `fetchTracksFromFirstAlbums`: the last fallback - a failure must not prevent the
     *  already-fetched albums from showing. */
    private suspend fun fetchTracksFromFirstAlbums(
        service: MusicService,
        albums: List<Album>,
    ): List<Track> = withContext(Dispatchers.IO) {
        try {
            albums.take(FALLBACK_ALBUM_COUNT).flatMap { album ->
                service.getAlbumAsDir(album.id, album.title, false).getTracks()
            }.distinctBy { it.id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") error: Exception) {
            emptyList()
        }
    }

    /** What one Artist Detail load produces. Server / domain objects; never reaches Compose. */
    data class ArtistData(
        val albums: List<Album>,
        val tracks: List<Track>,
        val biography: String?,
        val similarArtists: List<Artist>,
        val resolvedCoverArt: String?,
    )

    private companion object {
        private const val TRACK_FETCH_SIZE = 100
        private const val FALLBACK_ALBUM_COUNT = 3
    }
}
