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
}
