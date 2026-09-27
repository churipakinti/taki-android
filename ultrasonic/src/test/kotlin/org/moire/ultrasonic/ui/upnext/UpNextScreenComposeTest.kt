/*
 * UpNextScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.upnext

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Up Next (issue #10 phase 4K1) as a pure projection of [UpNextUiState]: structure, tap-to-play,
 * reorder (drag handle and accessibility actions), removal (swipe and accessibility action), the
 * long-press menu, empty/terminal states and semantics. The runtime is a recorder - the screen
 * owns no playback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h900dp-xxhdpi")
class UpNextScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val menu = listOf(UpNextMenuItem.LYRICS, UpNextMenuItem.FAVORITE)

    private val current = UpNextCurrentUi(title = "Kashmir", artist = "Led Zeppelin", artworkModel = null, isPlaying = true)

    private fun upcoming(vararg titles: String) = titles.mapIndexed { i, title ->
        UpNextTrackUi(key = "k$i", title = title, artist = "Artist $i", playOrderIndex = 3 + i, menuItems = menu)
    }

    private class Recorder {
        val events = mutableListOf<String>()
        fun actions() = UpNextActions(
            onBack = { events += "back" },
            onPlay = { events += "play:$it" },
            onMove = { from, to -> events += "move:$from>$to" },
            onRemove = { events += "remove:$it" },
            onMenuItem = { position, item -> events += "menu:$position:$item" },
        )
    }

    private fun show(
        state: UpNextUiState,
        recorder: Recorder = Recorder(),
        progress: Float = 0.4f,
    ): Recorder {
        compose.setContent {
            TakiTheme {
                UpNextScreen(
                    state = state,
                    progressFraction = { progress },
                    artworkFor = { null },
                    actions = recorder.actions(),
                )
            }
        }
        return recorder
    }

    private val populated = UpNextUiState(current = current, upcoming = upcoming("Immigrant Song", "Black Dog", "Rain Song"))

    // --- structure -----------------------------------------------------------------------------

    @Test
    fun `the header shows the Up Next title and a back button that fires onBack`() {
        val recorder = show(populated)
        compose.onNodeWithText("Up Next").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").performClick()
        assertEquals(listOf("back"), recorder.events)
    }

    @Test
    fun `the current track has its own section marked as now playing`() {
        show(populated)
        compose.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        compose.onNodeWithText("Kashmir").assertIsDisplayed()
        compose.onNodeWithText("Led Zeppelin").assertIsDisplayed()
        compose.onNodeWithTag(UP_NEXT_CURRENT_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `upcoming rows show title and artist`() {
        show(populated)
        compose.onNodeWithText("NEXT").assertIsDisplayed()
        compose.onNodeWithText("Immigrant Song").assertIsDisplayed()
        compose.onNodeWithText("Artist 0").assertIsDisplayed()
        compose.onNodeWithText("Black Dog").assertIsDisplayed()
    }

    @Test
    fun `the footer counts the tracks remaining`() {
        show(populated)
        compose.onNodeWithText("3 tracks remaining").assertIsDisplayed()
    }

    @Test
    fun `a single remaining track uses the singular`() {
        show(UpNextUiState(current = current, upcoming = upcoming("Only One")))
        compose.onNodeWithText("1 track remaining").assertIsDisplayed()
    }

    // --- empty / terminal states -----------------------------------------------------------------

    @Test
    fun `nothing upcoming shows the minimal message and no next section`() {
        show(UpNextUiState(current = current, upcoming = emptyList()))
        compose.onNodeWithText("Nothing up next").assertIsDisplayed()
        compose.onNodeWithText("NEXT").assertDoesNotExist()
    }

    @Test
    fun `an empty queue with no playback shows no stale rows`() {
        show(UpNextUiState())
        compose.onNodeWithText("NOW PLAYING").assertDoesNotExist()
        compose.onNodeWithText("Nothing up next").assertIsDisplayed()
    }

    // --- tap-to-play -----------------------------------------------------------------------------

    @Test
    fun `tapping an upcoming row plays that row's play-order position`() {
        val recorder = show(populated)
        compose.onNodeWithText("Black Dog").performClick()
        assertEquals(listOf("play:4"), recorder.events)
    }

    // --- long-press menu ---------------------------------------------------------------------------

    @Test
    fun `long-pressing a row offers its menu and dispatches the chosen item`() {
        val recorder = show(populated)
        compose.onNodeWithText("Immigrant Song").performTouchInput { longClick() }
        compose.onNodeWithText("Lyrics").assertIsDisplayed()
        compose.onNodeWithText("Like").performClick()
        assertEquals(listOf("menu:3:FAVORITE"), recorder.events)
        assertTrue("a long press must not also play the row", recorder.events.none { it.startsWith("play") })
    }

    // --- reorder / remove: accessibility actions (the non-gesture path) ------------------------------

    private fun customAction(text: String, label: String): Boolean {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        return compose.runOnIdle { action.action() }
    }

    @Test
    fun `every row exposes remove, and move up or down only where possible`() {
        show(populated)
        val first = compose.onNodeWithText("Immigrant Song").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Remove from queue", "Move down"), first.map { it.label })
        val middle = compose.onNodeWithText("Black Dog").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Remove from queue", "Move up", "Move down"), middle.map { it.label })
        val last = compose.onNodeWithText("Rain Song").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Remove from queue", "Move up"), last.map { it.label })
    }

    @Test
    fun `the remove action dispatches the row's play-order position`() {
        val recorder = show(populated)
        assertTrue(customAction("Black Dog", "Remove from queue"))
        assertEquals(listOf("remove:4"), recorder.events)
    }

    @Test
    fun `move down dispatches a play-order move from the row to the next position`() {
        val recorder = show(populated)
        assertTrue(customAction("Immigrant Song", "Move down"))
        assertEquals(listOf("move:3>4"), recorder.events)
    }

    @Test
    fun `move up dispatches a play-order move to the previous position`() {
        val recorder = show(populated)
        assertTrue(customAction("Rain Song", "Move up"))
        assertEquals(listOf("move:5>4"), recorder.events)
    }

    // --- reorder: drag handle ------------------------------------------------------------------------

    @Test
    fun `every upcoming row has a reorder handle`() {
        show(populated)
        compose.onAllNodesWithTag(UP_NEXT_HANDLE_TEST_TAG, useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun `dragging a handle past the next row commits one move and never plays the row`() {
        val recorder = show(populated)
        compose.onAllNodesWithTag(UP_NEXT_HANDLE_TEST_TAG, useUnmergedTree = true)[0].performTouchInput {
            down(center)
            moveBy(Offset(0f, 400f))
            up()
        }
        compose.waitForIdle()
        val moves = recorder.events.filter { it.startsWith("move") }
        assertEquals("events were ${recorder.events}", 1, moves.size)
        assertTrue("moved down from position 3: $moves", moves.single().startsWith("move:3>"))
        assertTrue("a drag must not play: ${recorder.events}", recorder.events.none { it.startsWith("play") })
    }

    // --- swipe to remove -------------------------------------------------------------------------------

    @Test
    fun `swiping a row away removes it through the runtime path`() {
        val recorder = show(populated)
        compose.onNodeWithText("Black Dog").performTouchInput {
            down(Offset(width * 0.7f, centerY))
            moveTo(Offset(width * 0.05f, centerY), delayMillis = 300)
            up()
        }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        assertEquals(listOf("remove:4"), recorder.events)
    }

    // --- semantics / touch targets ---------------------------------------------------------------------

    @Test
    fun `the current section announces itself as now playing`() {
        show(populated)
        val description = compose.onNodeWithTag(UP_NEXT_CURRENT_TEST_TAG).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertEquals("Now playing", description)
    }

    @Test
    fun `upcoming rows and handles keep at least a 48dp touch target`() {
        show(populated)
        compose.onNodeWithText("Black Dog").assertHeightIsAtLeast(48.dp)
        compose.onAllNodesWithTag(UP_NEXT_HANDLE_TEST_TAG, useUnmergedTree = true)[0]
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun `a row is announced with its title and artist`() {
        show(populated)
        val texts = compose.onNodeWithText("Black Dog").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
        assertTrue(texts.contains("Black Dog") && texts.contains("Artist 1"))
    }
}
