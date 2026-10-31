/*
 * TakiMenuTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_LABEL = "Add this entire extended edition to a playlist of long names"

/** The shared popup menu (pre-beta UI polish sprint): behaviour, touch targets and one golden. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h700dp-xxhdpi")
class TakiMenuTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Menu(onDismiss: () -> Unit = {}, onPlay: () -> Unit = {}, onDelete: () -> Unit = {}) {
        TakiTheme {
            Box(Modifier.fillMaxSize().background(TakiTheme.colors.black)) {
                TakiDropdownMenu(expanded = true, onDismissRequest = onDismiss) {
                    TakiMenuItem(text = "Play next", onClick = onPlay)
                    TakiMenuItem(text = LONG_LABEL, onClick = {})
                    TakiMenuItem(text = "Unavailable", onClick = {}, enabled = false)
                    TakiMenuDivider()
                    TakiMenuItem(text = "Delete", onClick = onDelete, destructive = true)
                }
            }
        }
    }

    @Test
    fun `every item meets the 48dp touch target and the long label is shown whole`() {
        compose.setContent { Menu() }
        listOf("Play next", LONG_LABEL, "Unavailable", "Delete").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
            compose.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `clicking an item fires only that action`() {
        var played = 0
        var deleted = 0
        compose.setContent { Menu(onPlay = { played++ }, onDelete = { deleted++ }) }
        compose.onNodeWithText("Play next").performClick()
        assertEquals(1, played)
        assertEquals(0, deleted)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `a disabled item cannot be activated`() {
        compose.setContent { Menu() }
        compose.onNodeWithText("Unavailable").assertIsNotEnabled()
    }

    @Test
    fun `menu golden`() {
        compose.setContent { Menu() }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/menu_standard.png")
    }
}
