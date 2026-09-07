/*
 * HomeActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

/**
 * Every side-effecting thing the Home screen can ask for. Supplied by the host Fragment,
 * which owns the `NavController` and the `MediaPlayerManager` command boundary (migration
 * plan section 3.2 / 8). Composables never touch navigation or playback directly.
 */
class HomeActions(
    val onRefresh: () -> Unit,
    val onOverflow: () -> Unit,
    val onAlbumClick: (HomeAlbumUi) -> Unit,
    val onOpenPlaylists: () -> Unit,
    val onOpenAlbums: () -> Unit,
    val onOpenArtists: () -> Unit,
    val onOpenSongs: () -> Unit,
    val onPlayMix: () -> Unit,
    val onOpenMix: () -> Unit,
    val onRegenerateMix: () -> Unit,
) {
    companion object {
        /** Inert set, for previews and screenshot tests. */
        val Noop = HomeActions(
            onRefresh = {},
            onOverflow = {},
            onAlbumClick = {},
            onOpenPlaylists = {},
            onOpenAlbums = {},
            onOpenArtists = {},
            onOpenSongs = {},
            onPlayMix = {},
            onOpenMix = {},
            onRegenerateMix = {},
        )
    }
}
