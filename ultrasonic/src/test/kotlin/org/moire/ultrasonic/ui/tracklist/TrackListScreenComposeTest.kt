/*
 * TrackListScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.tracklist

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
import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared Track List screen (issue #10 phase 4F1): the "Songs" controls row (sort menu +
 * "Play all"), the dedicated Liked Songs list (no controls, hearts visible), infinite scroll,
 * the per-track context menu (gated by [org.moire.ultrasonic.ui.album.TrackContextMenuState]),
 * and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class TrackListScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, title: String, subtitle: String = "", liked: Boolean = false) =
        TrackListRow(id = id, title = title, subtitle = subtitle, artworkModel = null, liked = liked)

    private val songsState = TrackListUiState(
        isLoading = false,
        showControls = true,
        sortOrder = SortOrder.ALL_SONGS,
        availableSortOrders = persistentListOf(SortOrder.ALL_SONGS, SortOrder.RANDOM, SortOrder.STARRED),
        rows = persistentListOf(
            row("t1", "OK Computer", "Radiohead · OK Computer"),
            row("t2", "Kid A", "Radiohead · Kid A"),
        ),
    )

    private val likedSongsState = TrackListUiState(
        isLoading = false,
        showControls = false,
        showHeart = true,
        sortOrder = SortOrder.STARRED,
        rows = persistentListOf(
            row("t1", "Combativo", "A.N.I.M.A.L. · Combativo", liked = true),
        ),
    )

    private fun setContent(state: TrackListUiState, actions: TrackListActions = TrackListActions.Noop) {
        compose.setContent {
            TakiTheme {
                TrackListScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(TRACK_LIST_CONTENT_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    private fun SemanticsNodeInteraction.longPress() = apply {
        performSemanticsAction(SemanticsActions.OnLongClick)
    }

    // --- rows / click --------------------------------------------------------------------

    @Test
    fun `every row shows its title and subtitle, and a tap opens it`() {
        var clicked: TrackListRow? = null
        setContent(songsState, TrackListActions.Noop.copy(onTrackClick = { clicked = it }))
        scrollTo("Kid A").assertIsDisplayed()
        scrollTo("Kid A").performClick()
        assertEquals("t2", clicked?.id)
    }

    // --- Songs controls (sort menu + Play all) ------------------------------------------------

    @Test
    fun `the Songs controls row shows the sort chip and a Play all action`() {
        var playAllCalls = 0
        setContent(songsState, TrackListActions.Noop.copy(onPlayAll = { playAllCalls++ }))
        compose.onNodeWithText("All songs").assertIsDisplayed()
        compose.onNodeWithText("Play all").performClick()
        assertEquals(1, playAllCalls)
    }

    @Test
    fun `the sort control can change the order`() {
        var selected: SortOrder? = null
        setContent(songsState, TrackListActions.Noop.copy(onSortOrderSelected = { selected = it }))
        compose.onNodeWithText("All songs").performClick()
        compose.onNodeWithText("Random").performClick()
        assertEquals(SortOrder.RANDOM, selected)
    }

    @Test
    fun `Liked Songs shows no controls row`() {
        setContent(likedSongsState)
        compose.onNodeWithText("Play all").assertDoesNotExist()
    }

    // --- infinite scroll -----------------------------------------------------------------------

    @Test
    fun `scrolling near the end of a long list fires onLoadMore`() {
        var loadMoreCalls = 0
        val many = (1..30).map { row("t$it", "Track $it") }
        setContent(
            songsState.copy(rows = persistentListOf(*many.toTypedArray())),
            TrackListActions.Noop.copy(onLoadMore = { loadMoreCalls++ }),
        )
        scrollTo("Track 30")
        assertTrue(loadMoreCalls > 0)
    }

    // --- heart (Liked Songs only) ----------------------------------------------------------

    @Test
    fun `Liked Songs rows show a filled heart and toggling fires onHeartToggle`() {
        var toggled: TrackListRow? = null
        setContent(likedSongsState, TrackListActions.Noop.copy(onHeartToggle = { toggled = it }))
        compose.onNodeWithContentDescription("Like").performClick()
        assertEquals("t1", toggled?.id)
    }

    @Test
    fun `the Songs screen shows no heart column even for a liked row`() {
        setContent(songsState.copy(rows = persistentListOf(row("t1", "Liked One", liked = true))))
        compose.onNodeWithContentDescription("Like").assertDoesNotExist()
    }

    // --- context menu ------------------------------------------------------------------------

    @Test
    fun `long-pressing a row opens its context menu with the gated items`() {
        val fired = mutableListOf<Pair<String, TrackContextAction>>()
        setContent(
            songsState,
            TrackListActions.Noop.copy(
                onContextAction = { r, a -> fired += r.id to a },
                trackContextMenuState = { TrackContextMenuState(canDelete = false) },
            ),
        )
        scrollTo("OK Computer").longPress()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Play Next").performClick()
        assertEquals(listOf("t1" to TrackContextAction.PLAY_NEXT), fired)
    }

    @Test
    fun `the row's menu button also opens the context menu, not just long-press`() {
        setContent(songsState)
        compose.onNodeWithText("Play Now").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Show More")[0].performClick()
        compose.onNodeWithText("Play Now").assertIsDisplayed()
    }

    // --- empty / loading state ---------------------------------------------------------------

    @Test
    fun `a finished empty list shows the empty state`() {
        setContent(songsState.copy(rows = persistentListOf(), availableSortOrders = persistentListOf()))
        compose.onNodeWithText("No matches", substring = true).assertIsDisplayed()
    }

    @Test
    fun `still loading with no rows does not show the empty state`() {
        setContent(TrackListUiState(isLoading = true, rows = persistentListOf()))
        compose.onNodeWithText("No matches", substring = true).assertDoesNotExist()
    }
}
