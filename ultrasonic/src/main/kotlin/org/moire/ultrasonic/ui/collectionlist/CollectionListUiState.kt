/*
 * CollectionListUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collectionlist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The immutable, presentation-ready state of the Compose Box Sets list screen (post-issue-#10
 * residual migration, phase 5A1). A straight projection of what the legacy
 * `CollectionListFragment`/`CollectionListModel` already loaded: `AlbumDao.withGrouping()`
 * resolved client-side by `CollectionResolver` - never a network fetch of its own.
 */
@Immutable
data class CollectionListUiState(
    val isLoading: Boolean = true,
    /** Tracked for parity with every other migrated list ViewModel's load-failure bookkeeping,
     *  though the legacy screen had no error UI of its own (see the phase 5A1 report) - this
     *  does not drive a distinct error state either, only `showEmpty`/`hasContent`. */
    val loadFailed: Boolean = false,
    val rows: ImmutableList<CollectionListRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One Box Set row/card. [covers] is up to 3 entries mirroring [org.moire.ultrasonic.domain
 * .MusicCollection.stackArtwork] (index 0 = front/dominant cover, 1 = middle, 2 = back) - an
 * entry is `null` when that member album has no cover art of its own, not when the layer is
 * absent; whether a layer is drawn at all is decided by [albumCount], exactly like the legacy
 * `StackedArtworkBinder`'s `back.isVisible = albums.size > 2` / `middle.isVisible = albums.size
 * > 1`.
 */
@Immutable
data class CollectionListRow(
    val id: String,
    val title: String,
    val albumCount: Int,
    val covers: ImmutableList<CoverArtRequest?> = persistentListOf(),
)
