/*
 * CollectionDetailUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The immutable, presentation-ready state of the Compose Collection Detail screen (issue #10
 * phase 4B). A projection of exactly what the legacy `CollectionDetailModel` produced: the
 * `CollectionResolver`-resolved [org.moire.ultrasonic.domain.MusicCollection] for one exact
 * `grouping` string, its member releases ("discs") in the resolver's numeric order, and the
 * "find missing discs" progress flag. No `Album` / service object reaches Compose.
 *
 * This screen is navigation-only, exactly as the legacy one was: it never fetches or shows a
 * single track. Tapping a member opens the already-migrated Compose Album Detail. There is no
 * collection / disc / track Play, Shuffle, download or context menu here because the legacy
 * screen had none - see docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md step 5b.
 */
@Immutable
data class CollectionDetailUiState(
    /** The exact `CollectionResolver`-resolved title used as the nav argument - never a
     *  fuzzy-matched or display-only value. */
    val grouping: String = "",
    /** The resolved collection title (identical to [grouping] today), or the grouping while
     *  the resolve is still in flight. */
    val title: String = "",
    val isLoading: Boolean = true,
    /** The resolve returned nothing on the very first load - the screen shows the same empty
     *  state the legacy `emptyView` showed. A failed *refresh* keeps the members on screen. */
    val loadFailed: Boolean = false,
    /** The user-initiated "find missing discs" crawl is running (legacy `isDiscovering`). */
    val isDiscovering: Boolean = false,
    val members: ImmutableList<CollectionMember> = persistentListOf(),
) {
    val hasContent: Boolean get() = members.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && members.isEmpty()

    /** Member release count - the header's "N discs" line, exactly like the legacy subtitle. */
    val discCount: Int get() = members.size
}

/**
 * One member release of a collection - a "disc" of the box set. A lightweight already-cached
 * [org.moire.ultrasonic.domain.Album] row projected to just what the card draws; opening it
 * navigates to Album Detail, which is where its track list is fetched.
 */
@Immutable
data class CollectionMember(
    val id: String,
    /** The album's `parent`, forwarded as the `parentId` nav argument unchanged. */
    val parent: String?,
    /** The resolver's disc position, or null when the album has none. The screen formats it
     *  per layout ("Disc N" in the grid, "N" in the list), matching the legacy adapter. */
    val discNumber: Int?,
    val title: String,
    /** The album's own cached `songCount`; the card shows "N tracks" when > 0. Never a reason
     *  to fetch the disc's tracks. */
    val trackCount: Long?,
    /** Coil model for the member's own cover, or null for the neutral placeholder. */
    val artworkModel: CoverArtRequest?,
)
