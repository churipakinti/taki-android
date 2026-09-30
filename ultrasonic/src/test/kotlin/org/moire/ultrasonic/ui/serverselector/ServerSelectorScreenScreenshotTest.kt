/*
 * ServerSelectorScreenScreenshotTest.kt
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
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Server Selector (post-issue-#10 residual migration, phase 5A2): the
 * populated list with an active + an inactive server, and the delete confirmation. Fully
 * deterministic - fake [ServerSelectorUiState], no network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class ServerSelectorScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(
        id: Int,
        name: String,
        position: Int = id,
        description: String? = "https://$name.example.com",
        isOffline: Boolean = false,
        isActive: Boolean = false,
    ) = ServerSelectorRow(
        id = id,
        position = position,
        name = name,
        description = description,
        color = null,
        isOffline = isOffline,
        isActive = isActive,
    )

    private val offline = row(id = -1, name = "Offline", position = 0, description = null, isOffline = true)
    private val home = row(id = 1, name = "Home Server", position = 1, isActive = true)
    private val office = row(id = 2, name = "Office Server", position = 2)

    private val standard = ServerSelectorUiState(rows = persistentListOf(offline, home, office))

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

    private fun screen(state: ServerSelectorUiState): @Composable () -> Unit =
        { ServerSelectorScreen(state = state, actions = ServerSelectorActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun serverSelectorStandard() = capture("server_selector_standard") { screen(standard)() }

    @Test
    fun serverSelectorDeleteConfirmation() = capture("server_selector_delete_confirmation") {
        screen(standard.copy(pendingDelete = office))()
    }
}
