/*
 * ArtistDetailUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/** How long a biography must be before the "Show more / less" toggle appears (legacy parity). */
const val ARTIST_BIOGRAPHY_COLLAPSE_LENGTH = 320

/** How many of the fetched top songs the "Popular" preview shows (legacy `POPULAR_TRACK_COUNT`). */
const val ARTIST_POPULAR_TRACK_COUNT = 5

/**
 * The immutable, presentation-ready state of the Compose Artist Detail screen (issue #10
 * phase 4C). A projection of exactly what the legacy `ArtistDetailModel` loaded - the
 * year-desc album sort, the top-songs -> search -> first-albums track fallback chain, the
 * HTML-stripped biography, the similar-artists list, and the issue #16 cover-art gap-fill. No
 * service / domain object reaches Compose.
 */
@Immutable
data class ArtistDetailUiState(
    val isLoading: Boolean = true,
    /** The load threw before anything was shown - the screen keeps whatever it had, or shows
     *  the same "No music was found" state the legacy `artist_detail_empty` showed. */
    val loadFailed: Boolean = false,
    val artistId: String? = null,
    val artistName: String = "",
    /** Coil model for the hero, from the supplied nav-arg cover art or the id the model
     *  resolved later (issue #16). Null -> the neutral artist placeholder. */
    val artworkModel: CoverArtRequest? = null,
    val albumCount: Int = 0,
    /** HTML-stripped `ArtistInfo.biography`, or null when empty. */
    val biography: String? = null,
    val albums: ImmutableList<ArtistAlbumUi> = persistentListOf(),
    val popularTracks: ImmutableList<ArtistTrackUi> = persistentListOf(),
    val similarArtists: ImmutableList<ArtistSimilarUi> = persistentListOf(),
) {
    val hasContent: Boolean get() = albums.isNotEmpty() || popularTracks.isNotEmpty()

    /** Loaded, nothing to show - render the empty state (legacy `loaded && tracks & albums empty`). */
    val showEmpty: Boolean get() = !isLoading && !hasContent

    val showAbout: Boolean get() = !biography.isNullOrEmpty()

    val biographyCollapsible: Boolean
        get() = (biography?.length ?: 0) > ARTIST_BIOGRAPHY_COLLAPSE_LENGTH
}

/** One album card in the artist's horizontal album shelf. Tap -> the phase-4A Album Detail. */
@Immutable
data class ArtistAlbumUi(
    val id: String,
    val parent: String?,
    val title: String,
    /** The album artist line (legacy `HomeAlbumDelegate` showed `album.artist`). */
    val subtitle: String,
    val artworkModel: CoverArtRequest?,
)

/** One row in the "Popular" preview (up to [ARTIST_POPULAR_TRACK_COUNT]). */
@Immutable
data class ArtistTrackUi(
    val id: String,
    /** 1-based rank, shown in the row's leading column. */
    val rank: String,
    val title: String,
    /** The track's album name (legacy secondary line). */
    val subtitle: String?,
    val duration: String?,
    val isVideo: Boolean,
)

/** One portrait in the "Similar artists" shelf. Tap -> Artist Detail for that artist. */
@Immutable
data class ArtistSimilarUi(
    val id: String,
    val name: String,
    val coverArt: String?,
    val artworkModel: CoverArtRequest?,
)

/**
 * What the host needs to open the Artist Detail load. Interpreted from the unchanged
 * `artistDetailFragment` nav arguments by `ArtistDetailFragment`.
 */
@Immutable
data class ArtistDetailArgs(
    val artistId: String,
    val artistName: String,
    /** The `artistCoverArt` nav arg - null when reached from a tapped artist name (issue #16),
     *  in which case the ViewModel resolves the artist's own id from the artist list. */
    val knownCoverArt: String?,
    val refresh: Boolean = false,
)
