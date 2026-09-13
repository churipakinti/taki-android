/*
 * TrackListUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.tracklist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.view.SortOrder

/**
 * The immutable, presentation-ready state of the shared Compose Track List screen (issue #10
 * phase 4F1; Genre tracks/Daily Mix added in phase 4F2). Backs every legacy
 * `TrackCollectionFragment` destination that rendered through `LibraryTrackBinder`:
 *
 * - "Songs" (`navArgs.libraryRoot`) - a filterable multi-mode browser: All Songs / Random / By
 *   Artist / By Genre / Liked, exactly like the legacy `FilterButtonBar` exposed.
 * - the dedicated Liked Songs destination (`navArgs.getStarred`) - a flat liked-only list, no
 *   filter bar ([showControls] false).
 * - the standalone Genre tracks destination (`navArgs.genreName`, from `SelectGenreFragment`) -
 *   a fixed single-genre paged list with a visible back+title [headerTitle], no filter bar.
 * - Daily Mix (`navArgs.dailyMix`, from `HomeFragment`) - a fixed, unpaged list, same header
 *   treatment as Genre tracks.
 *
 * Deliberately carries no current-track-marker field: the legacy `LibraryTrackBinder` every one
 * of these destinations uses has none either (unlike Compose Album Detail's `TakiTrackRow`), so
 * none is invented here.
 */
@Immutable
data class TrackListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val sortOrder: SortOrder = SortOrder.ALL_SONGS,
    /** Only populated for the "Songs" (`libraryRoot`) destination - mirrors
     *  `TrackCollectionFragment.getListOfSortOrders()` (`STARRED` only when online). Empty for
     *  the dedicated Liked Songs destination, which never showed a sort control. */
    val availableSortOrders: ImmutableList<SortOrder> = persistentListOf(),
    /** True only for the "Songs" destination - gates both the sort-order control and the
     *  legacy `FilterButtonBar`'s "Play all" primary action, which is shown unconditionally
     *  there (`ViewCapabilities.primaryAction = FilterPrimaryAction.PLAY_ALL`), unlike the
     *  overflow-menu "Play all" item every other mode uses (hidden here since the toolbar
     *  itself is hidden for this destination). */
    val showControls: Boolean = false,
    /** True only for the dedicated Liked Songs destination (`navArgs.getStarred`) - a fixed
     *  per-Fragment-instance flag from the legacy `LibraryTrackBinder(showHeart = navArgs
     *  .getStarred)`, *not* derived from [sortOrder]: switching the "Songs" screen's own sort
     *  to "Liked" does not turn hearts on there, exactly like the legacy screen. */
    val showHeart: Boolean = false,
    /** Non-null only for Genre tracks (the genre name) and Daily Mix (issue #10 phase 4F2) -
     *  the Compose port of the legacy shell-continuity fix's Fragment-owned `TakiScreenHeader`
     *  (back + title), drawn in place of the shared `content_navigation_header` those two
     *  destinations never used (`NavigationActivity.isLightweightHeaderTrackCollection`,
     *  unchanged by this phase). Null for "Songs" and Liked Songs, which draw no header. */
    val headerTitle: String? = null,
    val rows: ImmutableList<TrackListRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One row - a 1:1 projection of the legacy `LibraryTrackBinder` binding (`track.title`, an
 * "artist · album" subtitle joining only the non-blank parts, cover art, and [liked] only
 * meaningful when [TrackListUiState.showHeart] is true).
 */
@Immutable
data class TrackListRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkModel: CoverArtRequest?,
    val liked: Boolean = false,
)
