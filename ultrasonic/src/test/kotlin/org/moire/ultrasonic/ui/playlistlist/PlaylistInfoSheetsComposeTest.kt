/*
 * PlaylistInfoSheetsComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.components.TAKI_SHEET_DISMISS_ACTION_TEST_TAG
import org.moire.ultrasonic.ui.components.TAKI_SHEET_PRIMARY_ACTION_TEST_TAG
import org.moire.ultrasonic.ui.components.takiSheetScrimTestTag
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The playlist info + "Update Information" sheets (issue #10 phase 5A6, part of R3). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class PlaylistInfoSheetsComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val info = PlaylistInfoUiState(
        name = "Road trip",
        owner = "admin",
        comment = "Summer 24 https://example.com/road",
        songCount = "42",
        isPublic = true,
        created = "2024-06-01T10:15:30",
    )

    @Test
    fun `info lines match the legacy dialog text, public flag and date included`() {
        compose.setContent {
            TakiTheme {
                assertEquals(
                    listOf(
                        "Owner: admin",
                        "Comments: Summer 24 https://example.com/road",
                        "Song Count: 42",
                        "Public: true",
                        "Creation Date: 2024-06-01 10:15:30",
                    ),
                    playlistInfoLines(info),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `without a public flag only owner comments and count are listed`() {
        compose.setContent {
            TakiTheme {
                assertEquals(3, playlistInfoLines(info.copy(isPublic = null)).size)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the info sheet is titled with the playlist name and dismisses with OK`() {
        var dismissed = 0
        compose.setContent {
            TakiTheme { PlaylistInfoSheet(info, { dismissed++ }, bottomContentInset = 0.dp) }
        }
        compose.onNodeWithText("Road trip").assertIsDisplayed()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        compose.onNodeWithTag(takiSheetScrimTestTag(PLAYLIST_INFO_SHEET_TEST_TAG)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(3, dismissed)
    }

    private data class Submitted(val name: String, val comment: String, val isPublic: Boolean)

    private fun showForm(
        publicFlag: Boolean? = true,
        submitted: MutableList<Submitted> = mutableListOf(),
        onDismiss: () -> Unit = {},
    ): MutableList<Submitted> {
        compose.setContent {
            TakiTheme {
                UpdatePlaylistInfoSheet(
                    initialName = "Road trip",
                    initialComment = "Summer",
                    initialPublic = publicFlag,
                    onConfirm = { n, c, p -> submitted += Submitted(n, c, p) },
                    onDismiss = onDismiss,
                    bottomContentInset = 0.dp,
                )
            }
        }
        return submitted
    }

    @Test
    fun `the form is pre-filled from the playlist`() {
        showForm()
        compose.onNodeWithText("Update Information").assertIsDisplayed()
        compose.onNodeWithTag(UPDATE_PLAYLIST_NAME_FIELD_TEST_TAG).assertTextEquals("Name", "Road trip")
        compose.onNodeWithTag(UPDATE_PLAYLIST_COMMENT_FIELD_TEST_TAG).assertTextEquals("Comment", "Summer")
        compose.onNodeWithTag(UPDATE_PLAYLIST_PUBLIC_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun `OK submits the edited fields exactly once`() {
        val submitted = showForm()
        compose.onNodeWithTag(UPDATE_PLAYLIST_NAME_FIELD_TEST_TAG).performTextClearance()
        compose.onNodeWithTag(UPDATE_PLAYLIST_NAME_FIELD_TEST_TAG).performTextInput("Long drive")
        compose.onNodeWithTag(UPDATE_PLAYLIST_PUBLIC_TEST_TAG).performClick()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        assertEquals(listOf(Submitted("Long drive", "Summer", false)), submitted)
    }

    @Test
    fun `the form does not validate - a blank name is submitted as typed, like the legacy dialog`() {
        val submitted = showForm()
        compose.onNodeWithTag(UPDATE_PLAYLIST_NAME_FIELD_TEST_TAG).performTextClearance()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        assertEquals(1, submitted.size)
        assertEquals("", submitted.single().name)
    }

    @Test
    fun `the public checkbox is disabled and reports false when the server sent no flag`() {
        val submitted = showForm(publicFlag = null)
        compose.onNodeWithTag(UPDATE_PLAYLIST_PUBLIC_TEST_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        assertEquals(false, submitted.single().isPublic)
    }

    @Test
    fun `cancel scrim and Back change nothing`() {
        var dismissed = 0
        val submitted = showForm(onDismiss = { dismissed++ })
        compose.onNodeWithTag(TAKI_SHEET_DISMISS_ACTION_TEST_TAG).performClick()
        compose.onNodeWithTag(takiSheetScrimTestTag(UPDATE_PLAYLIST_INFO_SHEET_TEST_TAG)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(3, dismissed)
        assertTrue(submitted.isEmpty())
    }
}
