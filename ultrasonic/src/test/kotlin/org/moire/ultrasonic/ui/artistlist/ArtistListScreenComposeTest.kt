/*
 * ArtistListScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artistlist

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
 * Artist List screen (issue #10 phase 4E1): the id3-artist grid, the list layout toggle, the
 * folder-selector header for folder-mode servers, the sort-order picker, the per-row context
 * menu (Play Now/Next/Last, Start Radio, Download) and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class ArtistListScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, name: String, isIndex: Boolean = false) =
        ArtistListRow(id = id, name = name, artworkModel = null, isIndex = isIndex)

    private val loaded = ArtistListUiState(
        isLoading = false,
        rows = persistentListOf(row("a1", "Alpha"), row("a2", "Bravo")),
        availableSortOrders = persistentListOf(
            SortOrder.BY_NAME,
            SortOrder.RECENT,
            SortOrder.NEWEST,
            SortOrder.FREQUENT,
        ),
    )

    private fun setContent(state: ArtistListUiState, actions: ArtistListActions = ArtistListActions.Noop) {
        compose.setContent {
            TakiTheme {
                ArtistListScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(ARTIST_LIST_CONTENT_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    /** `combinedClickable` registers [SemanticsActions.OnLongClick]; invoking it directly is
     *  deterministic under Robolectric (unlike a timed touch gesture), same pattern as
     *  `AlbumDetailScreenComposeTest`. */
    private fun SemanticsNodeInteraction.longPress() = apply {
        performSemanticsAction(SemanticsActions.OnLongClick)
    }

    // --- grid (default) --------------------------------------------------------------------

    @Test
    fun `the grid layout shows every row and a tap opens it`() {
        var opened: ArtistListRow? = null
        setContent(
            loaded,
            ArtistListActions.Noop.copy(onEntryClick = { opened = it }),
        )
        scrollTo("Bravo").performClick()
        assertEquals("a2", opened?.id)
    }

    // --- list layout -------------------------------------------------------------------------

    @Test
    fun `the layout toggle button offers the other layout and fires it on tap`() {
        var requested: LayoutType? = null
        setContent(loaded, ArtistListActions.Noop.copy(onLayoutTypeSelected = { requested = it }))
        // In the default grid (COVER) layout, the button's own label offers "List" next.
        compose.onNodeWithContentDescription("List").performClick()
        assertEquals(LayoutType.LIST, requested)
    }

    @Test
    fun `in the list layout the toggle button offers Cover next`() {
        setContent(loaded.copy(layoutType = LayoutType.LIST))
        compose.onNodeWithContentDescription("Cover").assertIsDisplayed()
    }

    @Test
    fun `the list layout shows every row and a tap opens it`() {
        var opened: ArtistListRow? = null
        setContent(
            loaded.copy(layoutType = LayoutType.LIST),
            ArtistListActions.Noop.copy(onEntryClick = { opened = it }),
        )
        scrollTo("Alpha").performClick()
        assertEquals("a1", opened?.id)
    }

    // --- folder-mode / folder-selector header -------------------------------------------------

    @Test
    fun `folder-mode rows are shown the same way as id3 artists`() {
        setContent(
            loaded.copy(rows = persistentListOf(row("f1", "Rock", isIndex = true))),
        )
        scrollTo("Rock").assertIsDisplayed()
    }

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
            ArtistListActions.Noop.copy(onFolderSelected = { selected = it }),
        )
        compose.onNodeWithText("All Folders").performClick()
        compose.onNodeWithText("Classical").performClick()
        assertEquals("f1", selected)
    }

    // --- sort control --------------------------------------------------------------------

    @Test
    fun `the sort control shows the current selection and can change it`() {
        var selected: SortOrder? = null
        setContent(loaded, ArtistListActions.Noop.copy(onSortOrderSelected = { selected = it }))

        // BY_NAME is selected by default - its label ("Name") is the chip's own text.
        compose.onNodeWithText("Name").assertIsDisplayed()
        compose.onNodeWithText("Name").performClick()
        compose.onNodeWithText("Recently Played").performClick()
        assertEquals(SortOrder.RECENT, selected)
    }

    // --- context menu ------------------------------------------------------------------------

    @Test
    fun `long-pressing a row opens its context menu, a normal tap still opens the entry`() {
        var opened: ArtistListRow? = null
        val fired = mutableListOf<Pair<String, ArtistContextAction>>()
        setContent(
            loaded,
            ArtistListActions.Noop.copy(
                onEntryClick = { opened = it },
                onContextAction = { r, a -> fired += r.id to a },
            ),
        )

        scrollTo("Alpha").performClick()
        assertEquals("a1", opened?.id)
        compose.onNodeWithText("Play Now").assertDoesNotExist()

        scrollTo("Bravo").longPress()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
        compose.onNodeWithText("Play Next").performClick()
        assertEquals(listOf("a2" to ArtistContextAction.PLAY_NEXT), fired)
    }

    @Test
    fun `the context menu hides Download when offline`() {
        setContent(loaded.copy(downloadAvailable = false))
        scrollTo("Alpha").longPress()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    // --- empty state ---------------------------------------------------------------------

    @Test
    fun `a finished empty artist list shows the empty state`() {
        setContent(loaded.copy(rows = persistentListOf(), availableSortOrders = persistentListOf()))
        compose.onNodeWithText("No matches", substring = true).assertIsDisplayed()
    }

    @Test
    fun `still loading with no rows does not show the empty state`() {
        setContent(ArtistListUiState(isLoading = true, rows = persistentListOf()))
        compose.onNodeWithText("No matches", substring = true).assertDoesNotExist()
    }
}
