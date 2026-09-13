/*
 * TakiSortMenu.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.moire.ultrasonic.view.SortOrder

/**
 * A sort-order picker built from the existing Taki visual language (issue #10 phase 4E1): a
 * [TakiFilterChip] showing the current selection opens a Material3 [DropdownMenu] listing the
 * rest - the Compose equivalent of the legacy `FilterButtonBar`'s sort dropdown, generic over
 * [SortOrder] so a later phase (Album List) can reuse it unchanged with its own option list and
 * labels.
 */
@Composable
fun TakiSortMenu(
    options: List<SortOrder>,
    selected: SortOrder,
    onSelect: (SortOrder) -> Unit,
    label: @Composable (SortOrder) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        TakiFilterChip(
            label = label(selected),
            selected = false,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
