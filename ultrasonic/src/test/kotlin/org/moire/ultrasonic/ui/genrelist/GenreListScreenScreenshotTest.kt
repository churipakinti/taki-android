/*
 * GenreListScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.genrelist

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
 * Roborazzi goldens for Genres List (issue #10 phase 4G2): the populated 2-column grid and the
 * empty state. Fully deterministic - fake [GenreListUiState], no artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class GenreListScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(name: String) = GenreListRow(name = name, artworkModel = null)

    private val standard = GenreListUiState(
        isLoading = false,
        rows = persistentListOf(
            row("Alternative Rock"),
            row("Jazz"),
            row("Ambient"),
            row("Metal"),
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

    private fun screen(state: GenreListUiState): @Composable () -> Unit =
        { GenreListScreen(state = state, actions = GenreListActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun genreListStandard() = capture("genre_list_standard") { screen(standard)() }

    @Test
    fun genreListEmpty() = capture("genre_list_empty") {
        screen(standard.copy(rows = persistentListOf()))()
    }
}
