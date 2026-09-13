/*
 * ArtistDetailActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

/**
 * The navigation + playback callbacks the Compose Artist Detail screen fires. Every one is
 * implemented in `ArtistDetailFragment` (the host / navigation + playback boundary); the
 * screen owns no `NavController`, no `MediaPlayerManager`, no music service. Mirrors
 * `AlbumDetailActions` / `HomeActions` etc.
 */
class ArtistDetailActions(
    /** Back out of the artist screen - the top row's own affordance (this screen hides the
     *  Activity toolbar). Wired to `NavController.navigateUp()`. */
    val onBack: () -> Unit,
    /** Play the whole artist (`playTracksAndToast`, `isArtist = true`, `InsertionMode.CLEAR`). */
    val onPlay: () -> Unit,
    /** Start artist radio (the unchanged `ArtistRadioQueueBuilder` path + its toasts). */
    val onRadio: () -> Unit,
    /** Download the whole artist (`DownloadUtil.justDownload`, `isArtist = true`). */
    val onDownload: () -> Unit,
    /** Open an album from the shelf - navigates to the phase-4A Compose Album Detail. */
    val onAlbumClick: (ArtistAlbumUi) -> Unit,
    /** Play from a "Popular" preview row, keeping the full fetched track list queued after it. */
    val onTrackClick: (trackId: String) -> Unit,
    /** Open another Artist Detail from the "Similar artists" shelf. */
    val onSimilarArtistClick: (ArtistSimilarUi) -> Unit,
    /** Pull-to-refresh: re-fetch the artist from the server. */
    val onRefresh: () -> Unit,
) {
    companion object {
        val Noop = ArtistDetailActions(
            onBack = {},
            onPlay = {},
            onRadio = {},
            onDownload = {},
            onAlbumClick = {},
            onTrackClick = {},
            onSimilarArtistClick = {},
            onRefresh = {},
        )
    }
}
