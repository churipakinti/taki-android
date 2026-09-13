/*
 * TakiEntryGrid.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A fixed-column [LazyVerticalGrid] for a browse list's cover-art presentation (issue #10 phase
 * 4E1) - the first `LazyVerticalGrid` in the codebase; every prior Compose screen's grid-shaped
 * content (Home shelves, Library's 2x2 cards) used a fixed-size `Row`/`LazyRow` instead,
 * because none of them scrolled a variable-length collection vertically. No new visual
 * language: cell spacing matches [AlbumShelfItem]'s existing shelf spacing.
 */
@Composable
fun <T> TakiEntryGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp), // taki-raw-ok: zero, not a spacing token
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
    ) {
        items(items, key = key) { item -> itemContent(item) }
    }
}
