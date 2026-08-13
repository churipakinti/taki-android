/*
 * MusicCollection.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.domain

/**
 * A Collection / Box Set (docs/TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md) - a set of [Album]s
 * that share the same `grouping` metadata value, e.g. a 222-disc box set where each disc is its
 * own Navidrome album. Deliberately not a Room entity: it's a pure, cheap-to-recompute view over
 * already-cached [Album] rows (see [org.moire.ultrasonic.util.CollectionResolver] and
 * `AlbumDao.withGrouping()`), not a second parallel database.
 *
 * A Collection contains albums, never tracks directly - opening one must never require fetching
 * every member album's song list.
 */
data class MusicCollection(
    val id: String,
    val title: String,
    val albums: List<Album>
) {
    val albumCount: Int get() = albums.size

    /**
     * No dedicated Collection-level artwork exists via the Subsonic/OpenSubsonic API (only
     * per-album `coverArt`), so this is the documented fallback (docs/
     * TAKI_COLLECTIONS_BOXSETS_IMPLEMENTATION.md section 12): the first disc's own artwork.
     */
    val artworkAlbum: Album? get() = albums.firstOrNull()

    /**
     * Up to 3 albums for the stacked-cover presentation (docs/TAKI_BOXSETS_VISUAL_REDESIGN.md
     * sections 3-4): index 0 is the dominant/front cover (same album as [artworkAlbum]), 1 is the
     * middle layer, 2 is the back layer. Never more than 3 - a 222-disc set must never load all
     * its covers just to draw this effect.
     */
    val stackArtwork: List<Album> get() = albums.take(3)
}
