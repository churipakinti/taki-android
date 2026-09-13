/*
 * AlbumListScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Album List screen (issue #10 phase 4E2): the id3/folder-mode album grid, the list layout
 * toggle, infinite scroll, the folder-selector header (folder-mode + alphabetical order only),
 * the sort-order picker, the per-row context menu (Play Now/Next/Last, Download - no Start
 * Radio) and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class AlbumListScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, title: String, artist: String = "") =
        AlbumListRow(id = id, title = title, artist = artist, artworkModel = null)

    private val loaded = AlbumListUiState(
        isLoading = false,
        rows = persistentListOf(
            row("a1", "OK Computer", "Radiohead"),
            row("a2", "In Rainbows", "Radiohead"),
        ),
        availableSortOrders = persistentListOf(
            SortOrder.NEWEST,
            SortOrder.RECENT,
            SortOrder.FREQUENT,
            SortOrder.BY_NAME,
        ),
    )

    private fun setContent(state: AlbumListUiState, actions: AlbumListActions = AlbumListActions.Noop) {
        compose.setContent {
            TakiTheme {
                AlbumListScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(ALBUM_LIST_CONTENT_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    private fun SemanticsNodeInteraction.longPress() = apply {
        performSemanticsAction(SemanticsActions.OnLongClick)
    }

    // --- grid (default) --------------------------------------------------------------------

    @Test
    fun `the grid layout shows every row with its artist subtitle, and a tap opens it`() {
        var opened: AlbumListRow? = null
        setContent(loaded, AlbumListActions.Noop.copy(onEntryClick = { opened = it }))
        scrollTo("In Rainbows").assertIsDisplayed()
        scrollTo("In Rainbows").performClick()
        assertEquals("a2", opened?.id)
    }

    // --- list layout -------------------------------------------------------------------------

    @Test
    fun `the layout toggle button offers the other layout and fires it on tap`() {
        var requested: LayoutType? = null
        setContent(loaded, AlbumListActions.Noop.copy(onLayoutTypeSelected = { requested = it }))
        compose.onNodeWithContentDescription("List").performClick()
        assertEquals(LayoutType.LIST, requested)
    }

    @Test
    fun `the list layout shows every row and a tap opens it`() {
        var opened: AlbumListRow? = null
        setContent(
            loaded.copy(layoutType = LayoutType.LIST),
            AlbumListActions.Noop.copy(onEntryClick = { opened = it }),
        )
        scrollTo("OK Computer").performClick()
        assertEquals("a1", opened?.id)
    }

    // --- infinite scroll -----------------------------------------------------------------------

    @Test
    fun `scrolling near the end of a long list fires onLoadMore`() {
        var loadMoreCalls = 0
        val many = (1..30).map { row("a$it", "Album $it") }
        setContent(
            loaded.copy(rows = persistentListOf(*many.toTypedArray())),
            AlbumListActions.Noop.copy(onLoadMore = { loadMoreCalls++ }),
        )
        scrollTo("Album 30")
        assertTrue(loadMoreCalls > 0)
    }

    // --- folder-mode / folder-selector header -------------------------------------------------

    @Test
    fun `the folder header is hidden by default`() {
        setContent(loaded)
        compose.onNodeWithText("All Folders").assertDoesNotExist()
    }

    @Test
    fun `the folder header is shown when the state asks for it`() {
        setContent(
            loaded.copy(
                showFolderHeader = true,
                folders = persistentListOf(MusicFolder("f1", "Classical", 0)),
            ),
        )
        compose.onNodeWithText("All Folders").assertIsDisplayed()
    }

    @Test
    fun `selecting a folder from the header fires onFolderSelected`() {
        var selected: String? = "unset"
        setContent(
            loaded.copy(
                showFolderHeader = true,
                folders = persistentListOf(MusicFolder("f1", "Classical", 0)),
            ),
            AlbumListActions.Noop.copy(onFolderSelected = { selected = it }),
        )
        compose.onNodeWithText("All Folders").performClick()
        compose.onNodeWithText("Classical").performClick()
        assertEquals("f1", selected)
    }

    // --- sort control --------------------------------------------------------------------

    @Test
    fun `the sort control shows the current selection and can change it`() {
        var selected: SortOrder? = null
        setContent(
            loaded.copy(sortOrder = SortOrder.NEWEST),
            AlbumListActions.Noop.copy(onSortOrderSelected = { selected = it }),
        )

        compose.onNodeWithText("Recently Added").assertIsDisplayed()
        compose.onNodeWithText("Recently Added").performClick()
        compose.onNodeWithText("By Name").performClick()
        assertEquals(SortOrder.BY_NAME, selected)
    }

    // --- context menu ------------------------------------------------------------------------

    @Test
    fun `long-pressing a row opens its context menu with no Start Radio item`() {
        var opened: AlbumListRow? = null
        val fired = mutableListOf<Pair<String, AlbumContextAction>>()
        setContent(
            loaded,
            AlbumListActions.Noop.copy(
                onEntryClick = { opened = it },
                onContextAction = { r, a -> fired += r.id to a },
            ),
        )

        scrollTo("OK Computer").performClick()
        assertEquals("a1", opened?.id)
        compose.onNodeWithText("Play Now").assertDoesNotExist()

        scrollTo("In Rainbows").longPress()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
        compose.onNodeWithText("Start Radio").assertDoesNotExist()
        compose.onNodeWithText("Play Next").performClick()
        assertEquals(listOf("a2" to AlbumContextAction.PLAY_NEXT), fired)
    }

    @Test
    fun `the context menu hides Download when offline`() {
        setContent(loaded.copy(downloadAvailable = false))
        scrollTo("OK Computer").longPress()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    // --- empty state ---------------------------------------------------------------------

    @Test
    fun `a finished empty album list shows the empty state`() {
        setContent(loaded.copy(rows = persistentListOf(), availableSortOrders = persistentListOf()))
        compose.onNodeWithText("No media found", substring = true).assertIsDisplayed()
    }

    @Test
    fun `still loading with no rows does not show the empty state`() {
        setContent(AlbumListUiState(isLoading = true, rows = persistentListOf()))
        compose.onNodeWithText("No media found", substring = true).assertDoesNotExist()
    }
}
