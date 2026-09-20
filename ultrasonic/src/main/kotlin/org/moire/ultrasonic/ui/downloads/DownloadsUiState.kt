/*
 * DownloadsUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.downloads

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The immutable, presentation-ready state of the Compose Downloads screen (issue #10 phase
 * 4G3): the albums that currently have downloaded/pinned tracks in the local offline metadata
 * database, exactly what the legacy `DownloadsFragment` listed. There is no queue, no progress
 * and no per-item download state here - the legacy screen never showed any (it is a flat list of
 * finished local albums, identical online and offline).
 */
@Immutable
data class DownloadsUiState(
    val isLoading: Boolean = true,
    val rows: ImmutableList<DownloadedAlbumRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing downloaded and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One downloaded album. [songCount] is the real *local* track count (the legacy
 * `getDownloadedAlbums` overwrote the cached server-side count with it), not the album's full
 * length on the server.
 */
@Immutable
data class DownloadedAlbumRow(
    val id: String,
    val title: String,
    val artist: String?,
    val songCount: Int,
    val artworkModel: CoverArtRequest? = null,
)
