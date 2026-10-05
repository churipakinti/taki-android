/*
 * OverlaySheetsScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.album.AlbumInfoSheet
import org.moire.ultrasonic.ui.album.AlbumInfoUiState
import org.moire.ultrasonic.ui.library.LibraryHubSheet
import org.moire.ultrasonic.ui.playlist.AddToPlaylistOption
import org.moire.ultrasonic.ui.playlist.AddToPlaylistSheet
import org.moire.ultrasonic.ui.playlistlist.PlaylistInfoSheet
import org.moire.ultrasonic.ui.playlistlist.PlaylistInfoUiState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the transient overlays migrated in issue #10 phase 5A6: one
 * representative of each distinct structure (picker, info sheet, confirmation, the hub). The
 * remaining confirmations share the confirm layout and get no golden of their own. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class OverlaySheetsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .height(860.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Test
    fun addToPlaylist() = capture("overlay_add_to_playlist") {
        AddToPlaylistSheet(
            playlists = listOf("Road trip", "Focus", "Sunday morning", "Workout", "Late night")
                .mapIndexed { i, name -> AddToPlaylistOption("pl-$i", name) },
            onSelect = {},
            onDismiss = {},
            bottomContentInset = 96.dp,
        )
    }

    @Test
    fun albumInfo() = capture("overlay_album_info") {
        AlbumInfoSheet(
            info = AlbumInfoUiState(
                albumName = "Era Vulgaris",
                artist = "Queens of the Stone Age",
                year = "2007",
                songCount = 11,
                discCount = 1,
                description = "Era Vulgaris is the sixth studio album by the American rock band " +
                    "Queens of the Stone Age, recorded in Burbank and released in 2007. ".repeat(4),
                artworkModel = null,
            ),
            onDismiss = {},
            bottomContentInset = 96.dp,
        )
    }

    @Test
    fun playlistInfo() = capture("overlay_playlist_info") {
        PlaylistInfoSheet(
            info = PlaylistInfoUiState(
                name = "Road trip",
                owner = "admin",
                comment = "Summer 24",
                songCount = "42",
                isPublic = true,
                created = "2024-06-01T10:15:30",
            ),
            onDismiss = {},
            bottomContentInset = 96.dp,
        )
    }

    @Test
    fun confirmation() = capture("overlay_confirm_delete_playlist") {
        TakiConfirmSheet(
            title = "Confirm",
            message = "Do you want to delete Road trip",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {},
            onDismiss = {},
            bottomContentInset = 96.dp,
            sheetTestTag = "golden_confirm",
        )
    }

    @Test
    fun libraryHub() = capture("overlay_library_hub") {
        LibraryHubSheet(
            currentLibraryName = "Home server",
            onAction = {},
            onDismiss = {},
            bottomContentInset = 96.dp,
        )
    }
}
