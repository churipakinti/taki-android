/*
 * CreatePlaylistActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.createplaylist

import org.moire.ultrasonic.view.SortOrder

/**
 * What the host Fragment needs to wire up from [CreatePlaylistScreen]. `BY_ARTIST`/`BY_GENRE`
 * always open the shared `ItemSelectionDialogFragment` picker first (Fragment-hosted, the same
 * pattern the legacy screen and every other migrated filter-bar screen already use) - this class
 * only carries the *result* of that picker back in via [onSortOrderSelected]'s ordinary
 * `ALL_SONGS`/`RANDOM` path is immediate, matching legacy `onOrderChanged`.
 */
data class CreatePlaylistActions(
    val onSearchQueryChange: (String) -> Unit,
    val onSearchSubmit: () -> Unit,
    val onSearchClear: () -> Unit,
    val onSortOrderSelected: (SortOrder) -> Unit,
    val onTrackToggle: (CreatePlaylistTrackRow) -> Unit,
    val onLoadMore: () -> Unit,
    val onSave: () -> Unit,
) {
    companion object {
        val Noop = CreatePlaylistActions(
            onSearchQueryChange = {},
            onSearchSubmit = {},
            onSearchClear = {},
            onSortOrderSelected = {},
            onTrackToggle = {},
            onLoadMore = {},
            onSave = {},
        )
    }
}
