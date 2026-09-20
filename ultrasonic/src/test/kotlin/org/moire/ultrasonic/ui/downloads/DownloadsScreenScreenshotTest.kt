/*
 * DownloadsScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.downloads

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
 * Roborazzi goldens for Downloads (issue #10 phase 4G3): the populated list and the empty
 * state. Fully deterministic - fake [DownloadsUiState], no artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class DownloadsScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, title: String, artist: String?, songs: Int) =
        DownloadedAlbumRow(id = id, title = title, artist = artist, songCount = songs)

    private val standard = DownloadsUiState(
        isLoading = false,
        rows = persistentListOf(
            row("1", "A Night at the Opera", "Queen", 12),
            row("2", "Kind of Blue", "Miles Davis", 1),
            row("3", "An Album With A Deliberately Very Long Title That Must Ellipsize", "Various", 24),
            row("4", "Untitled", null, 7),
        ),
    )

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .heightIn(max = 900.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: DownloadsUiState): @Composable () -> Unit =
        { DownloadsScreen(state = state, actions = DownloadsActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun downloadsStandard() = capture("downloads_standard") { screen(standard)() }

    @Test
    fun downloadsEmpty() = capture("downloads_empty") {
        screen(standard.copy(rows = persistentListOf()))()
    }
}
