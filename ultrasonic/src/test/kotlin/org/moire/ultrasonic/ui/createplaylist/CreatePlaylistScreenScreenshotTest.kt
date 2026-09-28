/*
 * CreatePlaylistScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.createplaylist

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
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the Compose Create Playlist screen (issue #10 phase 4M2): a populated
 * picker with a mixed selection, and the empty state. Fully deterministic - fake
 * [CreatePlaylistUiState], no artwork network.
 * Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class CreatePlaylistScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, title: String, subtitle: String, selected: Boolean = false) =
        CreatePlaylistTrackRow(id = id, title = title, subtitle = subtitle, artworkModel = null, selected = selected)

    private val populated = CreatePlaylistUiState(
        isLoading = false,
        sortOrder = SortOrder.ALL_SONGS,
        selectedCount = 2,
        rows = persistentListOf(
            row("t1", "OK Computer", "Radiohead · OK Computer", selected = true),
            row("t2", "Kid A", "Radiohead · Kid A"),
            row("t3", "Rumours", "Fleetwood Mac · Rumours", selected = true),
            row("t4", "Abbey Road", "The Beatles · Abbey Road"),
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

    private fun screen(state: CreatePlaylistUiState): @Composable () -> Unit =
        {
            CreatePlaylistScreen(
                state = state,
                actions = CreatePlaylistActions.Noop,
                bottomContentInset = 0.dp,
            )
        }

    @Test
    fun createPlaylistPopulated() = capture("create_playlist_populated") { screen(populated)() }

    @Test
    fun createPlaylistEmpty() = capture("create_playlist_empty") {
        screen(populated.copy(rows = persistentListOf(), selectedCount = 0))()
    }
}
