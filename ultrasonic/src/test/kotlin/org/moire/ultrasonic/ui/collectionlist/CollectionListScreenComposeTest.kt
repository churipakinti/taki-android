/*
 * CollectionListScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collectionlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
 * Box Sets list screen (post-issue-#10 residual migration, phase 5A1): a fixed 2-column grid of
 * collection cards, pull-to-refresh, and the empty state - a 1:1 visual/behavioural port of the
 * legacy `CollectionListFragment` (`RecyclerView` + `GridLayoutManager(2)` +
 * `CollectionRowAdapter`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class CollectionListScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(title: String, albumCount: Int = 3) =
        CollectionListRow(id = "collection:${title.lowercase()}", title = title, albumCount = albumCount)

    private val loaded = CollectionListUiState(
        isLoading = false,
        rows = persistentListOf(row("Bach 333"), row("Mercury Living Presence"), row("Solti Ring")),
    )

    private fun setContent(state: CollectionListUiState, actions: CollectionListActions = CollectionListActions.Noop) {
        compose.setContent {
            TakiTheme {
                CollectionListScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(COLLECTION_LIST_CONTENT_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    // --- header ----------------------------------------------------------------------------

    @Test
    fun `the header shows Box Sets and a back action`() {
        setContent(loaded)
        compose.onNodeWithText("Box Sets").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    @Test
    fun `tapping back fires onBack`() {
        var tapped = false
        setContent(loaded, CollectionListActions.Noop.copy(onBack = { tapped = true }))
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(tapped)
    }

    // --- rows ----------------------------------------------------------------------------------

    @Test
    fun `every collection title is shown, in the given order`() {
        setContent(loaded)
        scrollTo("Bach 333").assertIsDisplayed()
        scrollTo("Mercury Living Presence").assertIsDisplayed()
        scrollTo("Solti Ring").assertIsDisplayed()
    }

    @Test
    fun `tapping a collection fires onCollectionClick with that row`() {
        var opened: CollectionListRow? = null
        setContent(loaded, CollectionListActions.Noop.copy(onCollectionClick = { opened = it }))
        scrollTo("Mercury Living Presence").performClick()
        assertEquals("Mercury Living Presence", opened?.title)
    }

    @Test
    fun `the disc count is shown next to each title`() {
        setContent(CollectionListUiState(isLoading = false, rows = persistentListOf(row("Bach 333", albumCount = 222))))
        scrollTo("222 discs").assertIsDisplayed()
    }

    // --- pull-to-refresh ---------------------------------------------------------------------

    @Test
    fun `a pull-down gesture on the grid fires onRefresh`() {
        var refreshed = false
        setContent(loaded, CollectionListActions.Noop.copy(onRefresh = { refreshed = true }))
        compose.onNodeWithTag(COLLECTION_LIST_CONTENT_TEST_TAG).performTouchInput { swipeDown() }
        assertTrue(refreshed)
    }

    // --- empty / loading state -----------------------------------------------------------------

    @Test
    fun `a finished empty collection list shows the empty state`() {
        setContent(loaded.copy(rows = persistentListOf()))
        compose.onNodeWithText("No Box Sets found yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `still loading with no rows does not show the empty state`() {
        setContent(CollectionListUiState(isLoading = true, rows = persistentListOf()))
        compose.onNodeWithText("No Box Sets found yet", substring = true).assertDoesNotExist()
    }
}
