/*
 * EditServerScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Edit Server (issue #10 phase 5A3): the onboarding form, the existing-
 * server editor, and its Advanced section expanded. Fully deterministic - fake
 * [EditServerUiState], no network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class EditServerScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val newState = EditServerUiState(mode = EditServerMode.New)
    private val existingState = EditServerUiState(
        mode = EditServerMode.Existing(1),
        name = "Home Server",
        address = "https://home.example.com",
        username = "joseph",
        password = "secret",
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

    private fun screen(state: EditServerUiState): @Composable () -> Unit =
        { EditServerScreen(state = state, actions = EditServerActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun editServerNewMode() = capture("edit_server_new_mode") { screen(newState)() }

    @Test
    fun editServerExistingMode() = capture("edit_server_existing_mode") { screen(existingState)() }

    @Test
    fun editServerAdvancedExpanded() = capture("edit_server_advanced_expanded") {
        screen(existingState.copy(advancedExpanded = true, allowSelfSignedCertificate = true))()
    }
}
