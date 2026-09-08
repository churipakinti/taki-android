/*
 * TakiScreenHeaderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lightweight back-nav header used by the Box Sets list (issue #10 phase 4B). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h920dp-xxhdpi")
class TakiScreenHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the title renders exactly once, next to a back button`() {
        compose.setContent {
            TakiTheme { TakiScreenHeader(onBack = {}, title = "Box Sets") }
        }
        compose.onNodeWithText("Box Sets").assertIsDisplayed()
        compose.onAllNodesWithText("Box Sets").assertCountEquals(1)
        compose.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    @Test
    fun `the back affordance fires onBack`() {
        var backs = 0
        compose.setContent {
            TakiTheme { TakiScreenHeader(onBack = { backs++ }, title = "Box Sets") }
        }
        compose.onNodeWithContentDescription("Go back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the trailing actions slot renders`() {
        compose.setContent {
            TakiTheme {
                TakiScreenHeader(onBack = {}, title = "Box Sets") {
                    Text("ACT")
                }
            }
        }
        compose.onNodeWithText("ACT").assertIsDisplayed()
    }

    @Test
    fun `without a title the header still renders the back button`() {
        compose.setContent {
            TakiTheme { TakiScreenHeader(onBack = {}) }
        }
        compose.onNodeWithContentDescription("Go back").assertIsDisplayed()
        compose.onAllNodesWithText("Box Sets").assertCountEquals(0)
    }
}
