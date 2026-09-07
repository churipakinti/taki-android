/*
 * LibraryScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the Library states. Fully deterministic - the only variable is
 * [LibraryUiState.boxSetsAvailable]. Update with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 * See ultrasonic/src/test/screenshots/README.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h2400dp-xxhdpi")
class LibraryScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(
        tag: String,
        widthDp: Int,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme {
                    Box(
                        modifier = Modifier
                            .testTag(tag)
                            .width(widthDp.dp)
                            .heightIn(max = 2400.dp)
                            .background(TakiTheme.colors.black),
                    ) {
                        content()
                    }
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Test
    fun libraryStandard() = capture("library_standard", widthDp = 412) {
        LibraryScreen(state = LibraryUiState(), actions = LibraryActions.Noop)
    }

    @Test
    fun libraryBoxSets() = capture("library_box_sets", widthDp = 412) {
        LibraryScreen(state = LibraryUiState(boxSetsAvailable = true), actions = LibraryActions.Noop)
    }

    @Test
    fun libraryCompact360() = capture("library_compact_360", widthDp = 360) {
        LibraryScreen(state = LibraryUiState(boxSetsAvailable = true), actions = LibraryActions.Noop)
    }

    @Test
    fun libraryFontScale130() = capture("library_font_1_30", widthDp = 412, fontScale = 1.30f) {
        LibraryScreen(state = LibraryUiState(boxSetsAvailable = true), actions = LibraryActions.Noop)
    }
}
