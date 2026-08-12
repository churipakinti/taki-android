/*
 * ArtistRadioQueueBuilder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.PerfMetrics
import timber.log.Timber

private const val ARTIST_RADIO_TARGET_SIZE = 30
private const val ARTIST_RADIO_MIN_SIZE = 15
private const val ARTIST_RADIO_TOP_SONGS = 60
private const val ARTIST_RADIO_RELATED_ARTIST_LIMIT = 5
private const val ARTIST_RADIO_RELATED_TOP_SONGS = 8
private const val ARTIST_RADIO_RANDOM_FILL_SIZE = 60
private const val ARTIST_RADIO_ALBUM_FALLBACK_COUNT = 6

class ArtistRadioQueueBuilder(private val musicService: MusicService) {
    companion object {
        // Exposed so callers can tell the user when the generated radio came back shorter than
        // usual (docs/TAKI_RADIOS_AND_DAILY_MIX.md section 7/9: "comunicar si la cola resultante
        // es más corta").
        const val TARGET_SIZE = ARTIST_RADIO_TARGET_SIZE
    }

    suspend fun build(artistId: String, artistName: String?): List<Track> {
        val perfToken = PerfMetrics.start("artist_radio_generate")
        val artistLabel = artistName?.takeIf { it.isNotBlank() } ?: artistId

        val artistInfo = fetch("artist_radio_artist_info") {
            musicService.getArtistInfo(artistId)
        }
        val relatedArtists = artistInfo
            ?.similarArtists
            .orEmpty()
            .take(ARTIST_RADIO_RELATED_ARTIST_LIMIT)

        val albums = fetch("artist_radio_albums") {
            musicService.getAlbumsOfArtist(artistId, artistName, false)
        }.orEmpty()

        val seedTracks = buildList {
            addAll(
                fetch("artist_radio_seed_top") {
                    musicService.getTopSongs(artistLabel, ARTIST_RADIO_TOP_SONGS)
                }.orEmpty()
            )
            addAll(fetchTracksFromAlbums(albums))
        }.filter { track ->
            track.artistId == artistId || track.artist.equals(artistName, ignoreCase = true)
        }

        val relatedTracks = relatedArtists.flatMap { artist ->
            val relatedName = artist.name?.takeIf { it.isNotBlank() } ?: return@flatMap emptyList()
            fetch("artist_radio_related_top") {
                musicService.getTopSongs(relatedName, ARTIST_RADIO_RELATED_TOP_SONGS)
            }.orEmpty()
        }

        val fillerTracks = fetch("artist_radio_random") {
            musicService.getRandomSongs(ARTIST_RADIO_RANDOM_FILL_SIZE).getTracks()
        }.orEmpty()

        val queue = ArtistRadioSelector.select(
            seedArtistId = artistId,
            seedArtistName = artistName,
            seedArtistTracks = seedTracks,
            relatedArtistTracks = relatedTracks,
            fillerTracks = fillerTracks,
            targetSize = ARTIST_RADIO_TARGET_SIZE,
            minimumSize = ARTIST_RADIO_MIN_SIZE
        )

        Timber.i(
            "Artist radio generated: seedArtist=%s relatedArtists=%d seedCandidates=%d " +
                "relatedCandidates=%d fillerCandidates=%d finalSize=%d",
            artistId,
            relatedArtists.size,
            seedTracks.distinctBy { it.id }.size,
            relatedTracks.distinctBy { it.id }.size,
            fillerTracks.distinctBy { it.id }.size,
            queue.size
        )
        PerfMetrics.end("artist_radio_generate", perfToken)

        return queue
    }

    private suspend fun fetchTracksFromAlbums(albums: List<Album>): List<Track> =
        albums.take(ARTIST_RADIO_ALBUM_FALLBACK_COUNT).flatMap { album ->
            fetch("artist_radio_album_tracks") {
                musicService.getAlbumAsDir(album.id, album.title, false).getTracks()
            }.orEmpty()
        }

    private suspend fun <T> fetch(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (error: Exception) {
        Timber.i(error, "%s unavailable while building artist radio", label)
        null
    }
}
