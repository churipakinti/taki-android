/*
 * LibraryHubSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.components.takiSheetScrimTestTag
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Library hub sheet (issue #10 phase 5A6): R4, replacing the AppCompat `PopupMenu`. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class LibraryHubSheetComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        actions: MutableList<LibraryHubAction> = mutableListOf(),
        onDismiss: () -> Unit = {},
    ): MutableList<LibraryHubAction> {
        compose.setContent {
            TakiTheme {
                LibraryHubSheet(
                    currentLibraryName = "Home server",
                    onAction = { actions += it },
                    onDismiss = onDismiss,
                    bottomContentInset = 0.dp,
                )
            }
        }
        return actions
    }

    @Test
    fun `lists exactly the legacy entries in the legacy order`() {
        show()
        compose.onNodeWithText("Your library").assertIsDisplayed()
        val order = listOf(
            LIBRARY_HUB_CURRENT_TEST_TAG,
            libraryHubActionTestTag(LibraryHubAction.SWITCH),
            libraryHubActionTestTag(LibraryHubAction.ADD),
            libraryHubActionTestTag(LibraryHubAction.SETTINGS),
            libraryHubActionTestTag(LibraryHubAction.ABOUT),
        )
        val tops = order.map { compose.onNodeWithTag(it).getBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithText("Switch collection").assertIsDisplayed()
        compose.onNodeWithText("Add collection").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun `the current library row is informational - shown, disabled, and never actionable`() {
        var dismissed = 0
        val actions = show(onDismiss = { dismissed++ })
        compose.onNodeWithText("Home server").assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_HUB_CURRENT_TEST_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Current library: Home server")))
        compose.onNodeWithTag(LIBRARY_HUB_CURRENT_TEST_TAG).performTouchInput { click() }
        assertTrue(actions.isEmpty())
        assertEquals("tapping the info row must not close the sheet (Pixel 7 regression)", 0, dismissed)
    }

    private fun assertActionFires(action: LibraryHubAction) {
        val actions = show()
        compose.onNodeWithTag(libraryHubActionTestTag(action)).performClick()
        assertEquals(listOf(action), actions)
    }

    @Test
    fun `Switch collection fires SWITCH`() = assertActionFires(LibraryHubAction.SWITCH)

    @Test
    fun `Add collection fires ADD`() = assertActionFires(LibraryHubAction.ADD)

    @Test
    fun `Settings fires SETTINGS`() = assertActionFires(LibraryHubAction.SETTINGS)

    @Test
    fun `About fires ABOUT`() = assertActionFires(LibraryHubAction.ABOUT)

    @Test
    fun `an action cannot fire twice from a double tap`() {
        val actions = show()
        compose.onNodeWithTag(libraryHubActionTestTag(LibraryHubAction.SETTINGS)).performClick()
        compose.onNodeWithTag(libraryHubActionTestTag(LibraryHubAction.ABOUT)).performClick()
        assertEquals(listOf(LibraryHubAction.SETTINGS), actions)
    }

    @Test
    fun `scrim and Back dismiss without navigating`() {
        var dismissed = 0
        val actions = show(onDismiss = { dismissed++ })
        compose.onNodeWithTag(takiSheetScrimTestTag(LIBRARY_HUB_SHEET_TEST_TAG)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(2, dismissed)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `actionable rows are at least 48dp tall`() {
        show()
        LibraryHubAction.values().forEach {
            val bounds = compose.onNodeWithTag(libraryHubActionTestTag(it)).getBoundsInRoot()
            assertTrue("${it.name} is ${bounds.height}", bounds.height >= 48.dp)
        }
    }
}
