/*
 * HomeMapping.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull

/**
 * Pure domain -> Home UI-model mapping, extracted from `HomeViewModel` so it can be unit
 * tested without Koin / a fake `MusicService`. Runs on a background dispatcher (the artwork
 * key is derived from a file path - see [coverArtRequestOrNull]).
 */
internal fun Album.toHomeAlbumUi(): HomeAlbumUi = HomeAlbumUi(
    id = id,
    title = title.orEmpty(),
    subtitle = artist.orEmpty(),
    artworkModel = coverArtRequestOrNull(large = false),
    isDirectory = isDirectory,
    parentId = parent,
)

/**
 * The daily mix as the featured card, or `null` when the mix is empty (parity with the old
 * `mixShelf.isVisible = tracks.isNotEmpty()`).
 */
internal fun mixToFeaturedUi(tracks: List<Track>): FeaturedMixUi? = when {
    tracks.isEmpty() -> null
    else -> FeaturedMixUi(
        trackCount = tracks.size,
        artworkModel = tracks.first().coverArtRequestOrNull(large = false),
    )
}
