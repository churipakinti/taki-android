/*
 * AlbumListScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the Album List (issue #10 phase 4E2): the album grid (default), the
 * list layout with artist subtitles, the folder-selector header (folder-mode, alphabetical
 * order) and the empty state. Fully deterministic - fake [AlbumListUiState], no artwork network.
 * Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class AlbumListScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, title: String, artist: String) =
        AlbumListRow(id = id, title = title, artist = artist, artworkModel = null)

    private val standard = AlbumListUiState(
        isLoading = false,
        rows = persistentListOf(
            row("a1", "OK Computer", "Radiohead"),
            row("a2", "Kid A", "Radiohead"),
            row("a3", "Rumours", "Fleetwood Mac"),
            row("a4", "Abbey Road", "The Beatles"),
            row("a5", "Nevermind", "Nirvana"),
            row("a6", "Thriller", "Michael Jackson"),
        ),
        availableSortOrders = persistentListOf(
            SortOrder.NEWEST,
            SortOrder.RECENT,
            SortOrder.FREQUENT,
            SortOrder.BY_NAME,
        ),
    )

    private fun capture(
        tag: String,
        widthDp: Int = 420,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(widthDp.dp)
                        .heightIn(max = 1200.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: AlbumListUiState): @Composable () -> Unit =
        {
            AlbumListScreen(
                state = state,
                actions = AlbumListActions.Noop,
                bottomContentInset = 0.dp,
            )
        }

    @Test
    fun albumListGrid() = capture("album_list_grid") { screen(standard)() }

    @Test
    fun albumListList() = capture("album_list_list") {
        screen(standard.copy(layoutType = LayoutType.LIST))()
    }

    @Test
    fun albumListFolderHeader() = capture("album_list_folder_header") {
        screen(
            standard.copy(
                sortOrder = SortOrder.BY_NAME,
                availableSortOrders = persistentListOf(SortOrder.BY_NAME, SortOrder.BY_ARTIST),
                showFolderHeader = true,
                folders = persistentListOf(MusicFolder("f1", "Classical", 0), MusicFolder("f2", "Jazz", 0)),
            ),
        )()
    }

    @Test
    fun albumListEmpty() = capture("album_list_empty") {
        screen(standard.copy(rows = persistentListOf(), availableSortOrders = persistentListOf()))()
    }
}
