/*
 * GenreListUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.genrelist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The immutable, presentation-ready state of the Compose Genres List screen (issue #10 phase
 * 4G2). A projection of what the legacy `SelectGenreFragment` already loads/derives:
 * `MusicService.getGenres` (already cache-backed server-side - see
 * [org.moire.ultrasonic.model.GenreListViewModel]'s kdoc) and a per-genre, visibility-driven,
 * semaphore-gated representative-cover resolve (`loadGenreCover`). There is **no grid/list
 * toggle and no sort menu** - the legacy screen was a fixed 2-column grid with a single,
 * non-configurable alphabetical order, unlike Album/Artist/Playlist List.
 */
@Immutable
data class GenreListUiState(
    val isLoading: Boolean = true,
    /** Tracked for parity with every other migrated list ViewModel's load-failure bookkeeping;
     *  like [org.moire.ultrasonic.model.PlaylistListViewModel]'s own `loadFailed`, this does not
     *  drive a distinct error UI - the legacy screen only ever showed a `Toast` on failure and
     *  otherwise left the screen exactly as it was, so this Compose screen does the same (no
     *  toast either, since a ViewModel cannot show one - a pre-existing-pattern, not a new gap
     *  introduced by this phase; see the phase 4G2 report). */
    val loadFailed: Boolean = false,
    val rows: ImmutableList<GenreListRow> = persistentListOf(),
    /** Bumped by a refresh, mirroring the ViewModel's own `coverGeneration` - [GenreListScreen]
     *  keys each cell's cover-fetch effect on this alongside the genre name, so a still-composed
     *  (never scrolled away) row re-requests its cover after a refresh clears it, exactly like
     *  the legacy `GenreAdapter.notifyDataSetChanged()` forcing every bound row to rebind. */
    val coverGeneration: Int = 0,
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One genre row/card. [artworkModel] is null until its representative cover resolves (or
 * resolves to "none found") - see the legacy `SelectGenreFragment.loadGenreCover`'s own kdoc on
 * why one representative track cover was chosen (genres have no cover art of their own).
 */
@Immutable
data class GenreListRow(
    val name: String,
    val artworkModel: CoverArtRequest? = null,
)
