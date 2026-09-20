/*
 * GenreListActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.genrelist

/**
 * Callbacks [GenreListScreen] needs from its host. [onCoverNeeded] is fired once per row the
 * first time it enters composition (a [androidx.compose.runtime.LaunchedEffect] keyed on the
 * genre name) - the Compose equivalent of the legacy `GenreAdapter.onBindViewHolder`'s
 * bind-driven `onCoverNeeded` call, so only genres actually scrolled into view ever trigger a
 * `getSongsByGenre` cover lookup, exactly like the legacy `RecyclerView`.
 */
data class GenreListActions(
    val onGenreClick: (GenreListRow) -> Unit,
    val onRefresh: () -> Unit,
    val onCoverNeeded: (String) -> Unit,
) {
    companion object {
        val Noop = GenreListActions(
            onGenreClick = {},
            onRefresh = {},
            onCoverNeeded = {},
        )
    }
}
