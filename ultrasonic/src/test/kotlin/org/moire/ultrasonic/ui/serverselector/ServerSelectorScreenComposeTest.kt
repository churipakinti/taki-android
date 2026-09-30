/*
 * ServerSelectorScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Server Selector screen (post-issue-#10 residual migration, phase 5A2): a 1:1 behavioural port
 * of the legacy `ServerSelectorFragment`/`ServerRowAdapter` list - server rows, the always-present
 * non-editable Offline row, active-state indication, Add/Edit/Delete, and the Compose delete
 * confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class ServerSelectorScreenComposeTest {

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
    private val home = row(id = 1, name = "Home", position = 1, isActive = true)
    private val away = row(id = 2, name = "Away", position = 2)

    private val loaded = ServerSelectorUiState(rows = persistentListOf(offline, home, away))

    private fun setContent(state: ServerSelectorUiState, actions: ServerSelectorActions = ServerSelectorActions.Noop) {
        compose.setContent {
            TakiTheme {
                ServerSelectorScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(SERVER_SELECTOR_CONTENT_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    // --- header ----------------------------------------------------------------------------

    @Test
    fun `the header shows the screen title and a back action`() {
        setContent(loaded)
        compose.onNodeWithText("Configured libraries").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    @Test
    fun `tapping back fires onBack`() {
        var tapped = false
        setContent(loaded, ServerSelectorActions.Noop.copy(onBack = { tapped = true }))
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(tapped)
    }

    // --- rows ----------------------------------------------------------------------------------

    @Test
    fun `every server name is shown, including Offline first`() {
        setContent(loaded)
        scrollTo("Offline").assertIsDisplayed()
        scrollTo("Home").assertIsDisplayed()
        scrollTo("Away").assertIsDisplayed()
    }

    @Test
    fun `a real server's url is shown, Offline's is not`() {
        setContent(loaded)
        scrollTo("https://Home.example.com").assertIsDisplayed()
        compose.onNodeWithText("http://localhost", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the active server shows the Active label, others do not`() {
        setContent(loaded)
        scrollTo("Active").assertIsDisplayed()
    }

    @Test
    fun `tapping a server row fires onServerClick with that row`() {
        var clicked: ServerSelectorRow? = null
        setContent(loaded, ServerSelectorActions.Noop.copy(onServerClick = { clicked = it }))
        scrollTo("Away").performClick()
        assertEquals("Away", clicked?.name)
    }

    @Test
    fun `tapping the offline row fires onServerClick too`() {
        var clicked: ServerSelectorRow? = null
        setContent(loaded, ServerSelectorActions.Noop.copy(onServerClick = { clicked = it }))
        scrollTo("Offline").performClick()
        assertEquals("Offline", clicked?.name)
    }

    // --- Edit / Delete overflow menu -----------------------------------------------------------

    @Test
    fun `the offline row has no overflow menu - it cannot be edited or deleted`() {
        setContent(loaded)
        compose.onNodeWithContentDescription("More options for Offline").assertDoesNotExist()
    }

    @Test
    fun `opening a real server's overflow menu and tapping Edit fires onEditServer`() {
        var edited: ServerSelectorRow? = null
        setContent(loaded, ServerSelectorActions.Noop.copy(onEditServer = { edited = it }))
        compose.onNodeWithContentDescription("More options for Home").performClick()
        compose.onNodeWithText("Edit").performClick()
        assertEquals("Home", edited?.name)
    }

    @Test
    fun `opening a real server's overflow menu and tapping Delete fires onDeleteRequested`() {
        var requested: ServerSelectorRow? = null
        setContent(loaded, ServerSelectorActions.Noop.copy(onDeleteRequested = { requested = it }))
        compose.onNodeWithContentDescription("More options for Away").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals("Away", requested?.name)
    }

    @Test
    fun `tapping the overflow menu does not also fire onServerClick`() {
        var clicked: ServerSelectorRow? = null
        var edited: ServerSelectorRow? = null
        setContent(
            loaded,
            ServerSelectorActions.Noop.copy(onServerClick = { clicked = it }, onEditServer = { edited = it }),
        )
        compose.onNodeWithContentDescription("More options for Home").performClick()
        compose.onNodeWithText("Edit").performClick()
        assertEquals("Home", edited?.name)
        assertEquals(null, clicked)
    }

    // --- Add server ------------------------------------------------------------------------

    @Test
    fun `tapping Add server fires onAddServer`() {
        var tapped = false
        setContent(loaded, ServerSelectorActions.Noop.copy(onAddServer = { tapped = true }))
        scrollTo("Add library").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `the add-server row is a real touch target at least 48dp tall`() {
        setContent(loaded)
        scrollTo("Add library").assertHeightIsAtLeast(48.dp)
    }

    // --- Delete confirmation -------------------------------------------------------------------

    @Test
    fun `the delete confirmation is hidden when there is no pending target`() {
        setContent(loaded)
        compose.onNodeWithTag(DELETE_SERVER_SHEET_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `the delete confirmation shows the exact pending server's name`() {
        setContent(loaded.copy(pendingDelete = away))
        compose.onNodeWithTag(DELETE_SERVER_SHEET_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DELETE_SERVER_NAME_TEST_TAG).assertTextEquals("Away")
    }

    @Test
    fun `tapping Cancel in the delete confirmation fires onDeleteCancel`() {
        var cancelled = false
        setContent(
            loaded.copy(pendingDelete = away),
            ServerSelectorActions.Noop.copy(onDeleteCancel = { cancelled = true }),
        )
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun `tapping Delete in the delete confirmation fires onDeleteConfirm`() {
        var confirmed = false
        setContent(
            loaded.copy(pendingDelete = away),
            ServerSelectorActions.Noop.copy(onDeleteConfirm = { confirmed = true }),
        )
        compose.onNodeWithTag(DELETE_SERVER_CONFIRM_TEST_TAG).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun `tapping the scrim fires onDeleteCancel`() {
        var cancelled = false
        setContent(
            loaded.copy(pendingDelete = away),
            ServerSelectorActions.Noop.copy(onDeleteCancel = { cancelled = true }),
        )
        compose.onNodeWithTag(DELETE_SERVER_SCRIM_TEST_TAG).performClick()
        assertTrue(cancelled)
    }
}
