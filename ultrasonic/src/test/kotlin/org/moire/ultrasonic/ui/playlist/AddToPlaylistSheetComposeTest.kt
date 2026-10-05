/*
 * AddToPlaylistSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.components.TAKI_SHEET_EMPTY_TEXT_TEST_TAG
import org.moire.ultrasonic.ui.components.takiSheetScrimTestTag
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Add-to-playlist picker (issue #10 phase 5A6): R1, replacing `ItemSelectionDialogFragment`. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class AddToPlaylistSheetComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val playlists = listOf(
        AddToPlaylistOption("pl-7", "Road trip"),
        AddToPlaylistOption("pl-2", "Focus"),
        AddToPlaylistOption("pl-9", "Focus"), // same name as pl-2: must stay distinguishable
        AddToPlaylistOption("pl-4", "Sunday morning"),
    )

    private fun show(
        list: List<AddToPlaylistOption> = playlists,
        onSelect: (String) -> Unit = {},
        onDismiss: () -> Unit = {},
        inset: androidx.compose.ui.unit.Dp = 0.dp,
    ) {
        compose.setContent {
            TakiTheme {
                AddToPlaylistSheet(list, onSelect, onDismiss, inset)
            }
        }
    }

    @Test
    fun `shows the title and every playlist in the server order`() {
        show()
        compose.onNodeWithText("Add to playlist").assertIsDisplayed()
        compose.onNodeWithText("Road trip").assertIsDisplayed()
        compose.onNodeWithText("Sunday morning").assertIsDisplayed()
        val tops = playlists.map { compose.onNodeWithTag(addToPlaylistOptionTestTag(it.id)).getBoundsInRoot().top }
        assertEquals("rows keep the server's order, not an alphabetical one", tops.sorted(), tops)
    }

    @Test
    fun `selecting a playlist reports its id exactly once`() {
        val picked = mutableListOf<String>()
        show(onSelect = { picked += it })
        compose.onNodeWithTag(addToPlaylistOptionTestTag("pl-4")).performClick()
        compose.onNodeWithTag(addToPlaylistOptionTestTag("pl-4")).performClick()
        assertEquals(listOf("pl-4"), picked)
    }

    @Test
    fun `two playlists with the same name are told apart by id`() {
        val picked = mutableListOf<String>()
        show(onSelect = { picked += it })
        compose.onNodeWithTag(addToPlaylistOptionTestTag("pl-9")).performClick()
        assertEquals(listOf("pl-9"), picked)
    }

    @Test
    fun `cancelling by the scrim or Back adds nothing`() {
        val picked = mutableListOf<String>()
        var dismissed = 0
        show(onSelect = { picked += it }, onDismiss = { dismissed++ })
        compose.onNodeWithTag(takiSheetScrimTestTag(ADD_TO_PLAYLIST_SHEET_TEST_TAG)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(2, dismissed)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `an empty list renders the no-playlists message instead of an empty panel`() {
        show(list = emptyList())
        compose.onNodeWithTag(TAKI_SHEET_EMPTY_TEXT_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("No saved playlists in this library").assertIsDisplayed()
    }

    @Test
    fun `the last playlist clears the floating-chrome inset`() {
        show(inset = 96.dp)
        val rootHeight = compose.onNodeWithTag(takiSheetScrimTestTag(ADD_TO_PLAYLIST_SHEET_TEST_TAG))
            .getBoundsInRoot().height
        val lastBottom = compose.onNodeWithTag(addToPlaylistOptionTestTag("pl-4")).getBoundsInRoot().bottom
        assertTrue(lastBottom <= rootHeight - 96.dp)
    }
}
