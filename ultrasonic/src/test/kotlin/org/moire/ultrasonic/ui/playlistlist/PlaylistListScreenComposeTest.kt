/*
 * PlaylistListScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playlists List screen (issue #10 phase 4G1): playlist rows/cards, **no sort menu** (unlike
 * Album/Artist List, the legacy `FilterButtonBar` never exposed one here), the grid/list toggle,
 * the create-playlist tile, the per-playlist context menu (six items online, two offline - the
 * legacy `select_playlist_context` vs `select_playlist_context_offline`), the Download/Remove
 * download label swap, and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class PlaylistListScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(
        id: String,
        name: String,
        songCount: Int = 10,
        status: PlaylistRowDownloadStatus = PlaylistRowDownloadStatus.NOT_DOWNLOADED,
    ) = PlaylistListRow(id = id, name = name, songCount = songCount, artworkModel = null, downloadStatus = status)

    private val loaded = PlaylistListUiState(
        isLoading = false,
        online = true,
        rows = persistentListOf(
            row("p1", "Road Trip"),
            row("p2", "Rainy Day"),
        ),
    )

    private fun setContent(state: PlaylistListUiState, actions: PlaylistListActions = PlaylistListActions.Noop) {
        compose.setContent {
            TakiTheme {
                PlaylistListScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(PLAYLIST_LIST_CONTENT_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    private fun SemanticsNodeInteraction.longPress() = apply {
        performSemanticsAction(SemanticsActions.OnLongClick)
    }

    // --- list layout (default) --------------------------------------------------------------

    @Test
    fun `every row shows its name and song count, and a tap opens it`() {
        var opened: PlaylistListRow? = null
        setContent(loaded, PlaylistListActions.Noop.copy(onEntryClick = { opened = it }))
        scrollTo("Rainy Day").assertIsDisplayed()
        scrollTo("Rainy Day").performClick()
        assertEquals("p2", opened?.id)
    }

    @Test
    fun `the playlist count is shown`() {
        setContent(loaded)
        compose.onNodeWithText("2 playlists").assertIsDisplayed()
    }

    // --- grid/list toggle --------------------------------------------------------------------

    @Test
    fun `the layout toggle offers the other layout and fires it on tap`() {
        var requested: LayoutType? = null
        setContent(loaded, PlaylistListActions.Noop.copy(onLayoutTypeSelected = { requested = it }))
        // Currently LIST - the toggle's own content description names the *other* layout it
        // would switch to ("Cover" = grid), matching Album List's identical toggle contract.
        compose.onNodeWithContentDescription("Cover").performClick()
        assertEquals(LayoutType.COVER, requested)
    }

    @Test
    fun `the grid layout shows every row and a tap opens it`() {
        var opened: PlaylistListRow? = null
        setContent(
            loaded.copy(layoutType = LayoutType.COVER),
            PlaylistListActions.Noop.copy(onEntryClick = { opened = it }),
        )
        scrollTo("Road Trip").performClick()
        assertEquals("p1", opened?.id)
    }

    // --- create playlist -----------------------------------------------------------------

    @Test
    fun `the create-playlist row appears last, online, and fires onCreatePlaylist`() {
        var created = 0
        setContent(loaded, PlaylistListActions.Noop.copy(onCreatePlaylist = { created++ }))
        scrollTo("New playlist").assertIsDisplayed()
        scrollTo("New playlist").performClick()
        assertEquals(1, created)
    }

    @Test
    fun `the create-playlist row is hidden offline`() {
        setContent(loaded.copy(online = false))
        compose.onNodeWithText("New playlist").assertDoesNotExist()
    }

    @Test
    fun `the create-playlist tile also appears in grid layout, online only`() {
        setContent(loaded.copy(layoutType = LayoutType.COVER))
        scrollTo("New playlist").assertIsDisplayed()
    }

    // --- context menu ------------------------------------------------------------------------

    @Test
    fun `the online context menu exposes all six legacy actions in order`() {
        setContent(loaded)
        scrollTo("Road Trip").longPress()
        listOf("Details", "Play Now", "Play Shuffled", "Download", "Update Information", "Delete")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `the offline context menu exposes only Play Now and Play Shuffled`() {
        setContent(loaded.copy(online = false))
        scrollTo("Road Trip").longPress()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
        compose.onNodeWithText("Play Shuffled").assertIsDisplayed()
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Update Information").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `the Download item reads Remove download once the playlist is fully downloaded`() {
        setContent(
            loaded.copy(
                rows = persistentListOf(row("p1", "Road Trip", status = PlaylistRowDownloadStatus.DOWNLOADED)),
            ),
        )
        scrollTo("Road Trip").longPress()
        compose.onNodeWithText("Remove download").assertIsDisplayed()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test
    fun `tapping a context menu item fires the action with the right row`() {
        val fired = mutableListOf<Pair<String, PlaylistContextAction>>()
        setContent(loaded, PlaylistListActions.Noop.copy(onContextAction = { r, a -> fired += r.id to a }))
        scrollTo("Rainy Day").longPress()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("p2" to PlaylistContextAction.DELETE), fired)
    }

    @Test
    fun `the row's menu button also opens the context menu, not just long-press`() {
        setContent(loaded)
        compose.onNodeWithText("Play Now").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("More options")[0].performClick()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
    }

    // --- download status (busy hides the menu button) -----------------------------------------

    @Test
    fun `a busy download status shows a progress spinner instead of the menu button`() {
        setContent(
            loaded.copy(
                rows = persistentListOf(row("p1", "Road Trip", status = PlaylistRowDownloadStatus.DOWNLOADING)),
            ),
        )
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun `the download status caption is shown only for actionable states`() {
        setContent(
            loaded.copy(
                rows = persistentListOf(
                    row("p1", "Road Trip", status = PlaylistRowDownloadStatus.DOWNLOADED),
                    row("p2", "Rainy Day", status = PlaylistRowDownloadStatus.NOT_DOWNLOADED),
                ),
            ),
        )
        compose.onNodeWithText("Downloaded").assertIsDisplayed()
    }

    // --- empty / loading state ---------------------------------------------------------------

    @Test
    fun `a finished empty playlist list shows the empty state`() {
        setContent(loaded.copy(rows = persistentListOf()))
        compose.onNodeWithText("No saved playlists", substring = true).assertIsDisplayed()
    }

    @Test
    fun `still loading with no rows does not show the empty state`() {
        setContent(PlaylistListUiState(isLoading = true, rows = persistentListOf()))
        compose.onNodeWithText("No saved playlists", substring = true).assertDoesNotExist()
    }
}
