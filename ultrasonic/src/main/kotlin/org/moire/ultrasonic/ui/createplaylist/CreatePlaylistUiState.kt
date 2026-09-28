/*
 * CreatePlaylistUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.createplaylist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.view.SortOrder

/**
 * The immutable, presentation-ready state of the Compose Create Playlist screen (issue #10 phase
 * 4M2) - a 1:1 projection of the legacy `CreatePlaylistFragment`: a track picker over the whole
 * library (All Songs/Random/By Artist/By Genre/free-text search), a running multi-selection, and
 * one Save action that always *creates* a new server playlist.
 *
 * There is no edit mode: the legacy screen (and this port) never loads an existing playlist's
 * membership, never updates or deletes one, and has no reorder - `getMusicService()
 * .createPlaylist(id = null, ...)` is the only call this screen ever makes. "Editing" an
 * already-created playlist's membership happens elsewhere (Playlist Detail's own remove-track
 * action, issue #10 phase 4F3), a different destination entirely.
 */
@Immutable
data class CreatePlaylistUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val sortOrder: SortOrder = SortOrder.ALL_SONGS,
    val searchQuery: String = "",
    val selectedCount: Int = 0,
    val rows: ImmutableList<CreatePlaylistTrackRow> = persistentListOf(),
) {
    /** Mirrors the legacy `saveButton?.isEnabled = count > 0`, plus a busy-guard the legacy
     *  screen didn't need (it disabled the button *and* re-enabled it only on error, but never
     *  raced a second tap in practice since `setLoading(true)` also ran first - this is the same
     *  effect, made explicit). */
    val saveEnabled: Boolean get() = selectedCount > 0 && !isSaving

    /** Nothing to show and not still loading - render the legacy `playlist_song_empty` text. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/** One row - the legacy `PlaylistTrackPickerBinder`'s exact fields: title, "artist · album"
 *  subtitle, cover art, and the selection checkbox. Tap anywhere on the row toggles it - there is
 *  no playback preview, no per-track context menu, and no long-press in the legacy picker either. */
@Immutable
data class CreatePlaylistTrackRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkModel: CoverArtRequest?,
    val selected: Boolean,
)
