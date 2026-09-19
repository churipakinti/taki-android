/*
 * PlaylistListScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

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
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Playlists List (issue #10 phase 4G1): the default list layout (name,
 * song count, download status, the create-playlist row), the grid layout, and the empty state.
 * Fully deterministic - fake [PlaylistListUiState], no artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class PlaylistListScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, name: String, songCount: Int, status: PlaylistRowDownloadStatus) =
        PlaylistListRow(id = id, name = name, songCount = songCount, artworkModel = null, downloadStatus = status)

    private val standard = PlaylistListUiState(
        isLoading = false,
        online = true,
        rows = persistentListOf(
            row("p1", "Road Trip", 24, PlaylistRowDownloadStatus.DOWNLOADED),
            row("p2", "Rainy Day", 12, PlaylistRowDownloadStatus.NOT_DOWNLOADED),
            row("p3", "Workout Mix", 30, PlaylistRowDownloadStatus.PARTIAL),
        ),
    )

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .heightIn(max = 1200.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: PlaylistListUiState): @Composable () -> Unit =
        { PlaylistListScreen(state = state, actions = PlaylistListActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun playlistListStandard() = capture("playlist_list_standard") { screen(standard)() }

    @Test
    fun playlistListGrid() = capture("playlist_list_grid") {
        screen(standard.copy(layoutType = LayoutType.COVER))()
    }

    @Test
    fun playlistListEmpty() = capture("playlist_list_empty") {
        screen(standard.copy(rows = persistentListOf()))()
    }
}
