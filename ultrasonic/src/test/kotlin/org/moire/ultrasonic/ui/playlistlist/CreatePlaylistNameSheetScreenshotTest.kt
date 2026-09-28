/*
 * CreatePlaylistNameSheetScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

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
 * Roborazzi goldens for the Compose Create Playlist naming sheet (issue #10 phase 4M3),
 * replacing the legacy `AlertDialog`: the blank/default state and the validation-error state
 * (both visually meaningful - the error message is a real, distinct rendering, not just a state
 * flag). Fully deterministic - `Noop` actions.
 * Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h800dp-xxhdpi")
class CreatePlaylistNameSheetScreenshotTest {

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
    fun createPlaylistNameSheet() = capture("create_playlist_name_sheet_golden") {
        CreatePlaylistNameSheet(
            visible = true,
            name = "",
            errorMessage = null,
            actions = CreatePlaylistNameActions.Noop,
        )
    }

    @Test
    fun createPlaylistNameSheetError() = capture("create_playlist_name_sheet_error_golden") {
        CreatePlaylistNameSheet(
            visible = true,
            name = "",
            errorMessage = "Enter a playlist name.",
            actions = CreatePlaylistNameActions.Noop,
        )
    }
}
