/*
 * FolderBrowserScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.folderbrowser

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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the Compose folder/non-ID3 browser (issue #10 phase 4M1): a directory
 * with mixed sub-folder + track rows (the one shape unique to this screen), and the empty state.
 * Fully deterministic - fake [FolderBrowserUiState], no artwork network.
 * Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class FolderBrowserScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val mixed = FolderBrowserUiState(
        isLoading = false,
        title = "Rock",
        rows = persistentListOf(
            FolderBrowserRow.Directory(
                id = "d1",
                title = "Live at the Fillmore",
                artist = "The Band",
                artworkModel = null,
                parent = "dir1",
            ),
            FolderBrowserRow.Track(
                id = "t1",
                title = "Rag Mama Rag",
                subtitle = "The Band · The Band",
                artworkModel = null,
            ),
            FolderBrowserRow.Track(
                id = "t2",
                title = "The Weight",
                subtitle = "The Band · Music from Big Pink",
                artworkModel = null,
                liked = true,
            ),
            FolderBrowserRow.Directory(
                id = "d2",
                title = "B-Sides",
                artist = null,
                artworkModel = null,
                parent = "dir1",
            ),
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

    private fun screen(state: FolderBrowserUiState): @Composable () -> Unit =
        {
            FolderBrowserScreen(
                state = state,
                actions = FolderBrowserActions.Noop,
                bottomContentInset = 0.dp,
            )
        }

    @Test
    fun folderBrowserMixed() = capture("folder_browser_mixed") { screen(mixed)() }

    @Test
    fun folderBrowserEmpty() = capture("folder_browser_empty") {
        screen(mixed.copy(rows = persistentListOf()))()
    }
}
