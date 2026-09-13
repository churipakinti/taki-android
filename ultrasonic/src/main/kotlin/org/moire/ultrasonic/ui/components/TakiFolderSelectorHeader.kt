/*
 * TakiFolderSelectorHeader.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The Compose equivalent of the legacy `FolderSelectorBinder` row (issue #10 phase 4E1): shows
 * the active music folder (or "All Folders"), tap opens a menu of every folder on the server.
 * Shown only for online, non-id3 (folder-mode) servers - the same condition
 * `ArtistListViewModel.showFolderHeaderNow` already gates on. The caller owns what happens on
 * selection (the Fragment round-trips through the existing `RxBus` folder-changed contract so
 * the still-legacy Album List reacts to the same change, exactly as it did before this phase).
 */
@Composable
fun TakiFolderSelectorHeader(
    folders: List<MusicFolder>,
    selectedFolderId: String?,
    onFolderSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val allFoldersLabel = stringResource(R.string.select_artist_all_folders)
    val currentLabel = folders.firstOrNull { it.id == selectedFolderId }?.name ?: allFoldersLabel

    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = { expanded = true })
                .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentLabel,
                style = TakiTheme.type.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = TakiTheme.colors.gray,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allFoldersLabel) },
                onClick = {
                    expanded = false
                    onFolderSelected(null)
                },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = {
                        expanded = false
                        onFolderSelected(folder.id)
                    },
                )
            }
        }
    }
}
