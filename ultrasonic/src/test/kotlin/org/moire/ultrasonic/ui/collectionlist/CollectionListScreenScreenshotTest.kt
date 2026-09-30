/*
 * CollectionListScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collectionlist

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
 * Roborazzi goldens for the Box Sets list (post-issue-#10 residual migration, phase 5A1): the
 * populated 2-column grid and the empty state. Fully deterministic - fake [CollectionListUiState],
 * no artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class CollectionListScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(title: String, albumCount: Int) =
        CollectionListRow(id = "collection:${title.lowercase()}", title = title, albumCount = albumCount)

    private val standard = CollectionListUiState(
        isLoading = false,
        rows = persistentListOf(
            row("Bach 333", albumCount = 222),
            row("Mercury Living Presence", albumCount = 2),
            row("Solti Ring", albumCount = 1),
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

    private fun screen(state: CollectionListUiState): @Composable () -> Unit =
        { CollectionListScreen(state = state, actions = CollectionListActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun collectionListStandard() = capture("collection_list_standard") { screen(standard)() }

    @Test
    fun collectionListEmpty() = capture("collection_list_empty") {
        screen(standard.copy(rows = persistentListOf()))()
    }
}
