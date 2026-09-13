/*
 * ArtistListScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artistlist

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
 * Roborazzi goldens for the Artist List (issue #10 phase 4E1): the id3 grid (default), the list
 * layout, the folder-selector header (folder-mode server) and the empty state. Fully
 * deterministic - fake [ArtistListUiState], no artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class ArtistListScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, name: String, isIndex: Boolean = false) =
        ArtistListRow(id = id, name = name, artworkModel = null, isIndex = isIndex)

    private val standard = ArtistListUiState(
        isLoading = false,
        rows = persistentListOf(
            row("a1", "AC/DC"),
            row("a2", "Bach"),
            row("a3", "The Cure"),
            row("a4", "Duran Duran"),
            row("a5", "Extreme"),
            row("a6", "Foo Fighters"),
        ),
        availableSortOrders = persistentListOf(
            SortOrder.BY_NAME,
            SortOrder.RECENT,
            SortOrder.NEWEST,
            SortOrder.FREQUENT,
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

    private fun screen(state: ArtistListUiState): @Composable () -> Unit =
        {
            ArtistListScreen(
                state = state,
                actions = ArtistListActions.Noop,
                bottomContentInset = 0.dp,
            )
        }

    @Test
    fun artistListGrid() = capture("artist_list_grid") { screen(standard)() }

    @Test
    fun artistListList() = capture("artist_list_list") {
        screen(standard.copy(layoutType = LayoutType.LIST))()
    }

    @Test
    fun artistListFolderHeader() = capture("artist_list_folder_header") {
        screen(
            standard.copy(
                rows = persistentListOf(row("f1", "Classical", isIndex = true), row("f2", "Jazz", isIndex = true)),
                availableSortOrders = persistentListOf(),
                showFolderHeader = true,
                folders = persistentListOf(MusicFolder("f1", "Classical", 0), MusicFolder("f2", "Jazz", 0)),
            ),
        )()
    }

    @Test
    fun artistListEmpty() = capture("artist_list_empty") {
        screen(standard.copy(rows = persistentListOf(), availableSortOrders = persistentListOf()))()
    }
}
