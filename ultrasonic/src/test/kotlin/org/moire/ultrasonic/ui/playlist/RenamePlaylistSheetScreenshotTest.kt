/*
 * RenamePlaylistSheetScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
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
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the Compose Rename Playlist sheet (issue #10 phase 4M4), replacing the
 * legacy `AlertDialog` (`R.layout.create_playlist`, reused for renaming): the name pre-filled with
 * the playlist's current name, and the validation-error state (a real, distinct rendering, not
 * just a state flag). Fully deterministic - `Noop` actions.
 * Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h800dp-xxhdpi")
class RenamePlaylistSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(tag: String, content: @Composable BoxScope.() -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .heightIn(max = 800.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Test
    fun renamePlaylistSheet() = capture("rename_playlist_sheet_golden") {
        RenamePlaylistSheet(
            visible = true,
            name = "Road Trip",
            errorMessage = null,
            actions = RenamePlaylistActions.Noop,
            bottomContentInset = 0.dp,
        )
    }

    @Test
    fun renamePlaylistSheetError() = capture("rename_playlist_sheet_error_golden") {
        RenamePlaylistSheet(
            visible = true,
            name = "",
            errorMessage = "Enter a playlist name.",
            actions = RenamePlaylistActions.Noop,
            bottomContentInset = 0.dp,
        )
    }
}
