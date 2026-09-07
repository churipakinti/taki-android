/*
 * LibraryActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

/**
 * Every navigation the Library screen can trigger. Supplied by the host Fragment, which owns
 * the `NavController` (migration plan section 3.2). One callback per row, plus the header
 * overflow - composables never call `findNavController()` themselves.
 *
 * Order here is documentation only; [LibraryScreen] fixes the on-screen order.
 */
class LibraryActions(
    val onOverflow: () -> Unit,
    // "Your music"
    val onLikedSongs: () -> Unit,
    val onLikedAlbums: () -> Unit,
    val onPlaylists: () -> Unit,
    val onDownloads: () -> Unit,
    // "Browse your collection"
    val onAlbums: () -> Unit,
    val onArtists: () -> Unit,
    val onSongs: () -> Unit,
    val onGenres: () -> Unit,
    val onBoxSets: () -> Unit,
) {
    companion object {
        /** Inert set, for previews and screenshot tests. */
        val Noop = LibraryActions(
            onOverflow = {},
            onLikedSongs = {},
            onLikedAlbums = {},
            onPlaylists = {},
            onDownloads = {},
            onAlbums = {},
            onArtists = {},
            onSongs = {},
            onGenres = {},
            onBoxSets = {},
        )
    }
}
