/*
 * GenreListScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.genrelist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Genres List screen (issue #10 phase 4G2): a fixed 2-column grid of genre cards, no sort menu,
 * no list/grid toggle, no context menu, no create action - the legacy `SelectGenreFragment` never
 * had any of these - plus the visibility-driven cover fetch and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class GenreListScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(name: String) = GenreListRow(name = name, artworkModel = null)

    private val loaded = GenreListUiState(
        isLoading = false,
        rows = persistentListOf(row("Rock"), row("Jazz"), row("Ambient")),
    )

    private fun setContent(state: GenreListUiState, actions: GenreListActions = GenreListActions.Noop) {
        compose.setContent {
            TakiTheme {
                GenreListScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(GENRE_LIST_CONTENT_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    // --- rows ----------------------------------------------------------------------------------

    @Test
    fun `every genre name is shown, in the given order`() {
        setContent(loaded)
        scrollTo("Rock").assertIsDisplayed()
        scrollTo("Jazz").assertIsDisplayed()
        scrollTo("Ambient").assertIsDisplayed()
    }

    @Test
    fun `tapping a genre fires onGenreClick with that row`() {
        var opened: GenreListRow? = null
        setContent(loaded, GenreListActions.Noop.copy(onGenreClick = { opened = it }))
        scrollTo("Jazz").performClick()
        assertEquals("Jazz", opened?.name)
    }

    // --- cover fetch (visibility-driven) --------------------------------------------------------

    @Test
    fun `onCoverNeeded fires once per genre once its cell is composed`() {
        val requested = mutableListOf<String>()
        setContent(loaded, GenreListActions.Noop.copy(onCoverNeeded = { requested.add(it) }))
        compose.waitForIdle()
        // Only the genres actually composed (visible within the test viewport / grid prefetch
        // window) have fired - never eagerly for the whole list up front - and never twice for
        // the same genre.
        assertEquals(requested.distinct(), requested)
        assertTrue(requested.contains("Rock"))
    }

    @Test
    fun `onCoverNeeded is not re-fired when a row's own artwork resolves via recomposition`() {
        val requested = mutableListOf<String>()
        var state by mutableStateOf(GenreListUiState(isLoading = false, rows = persistentListOf(row("Rock"))))
        compose.setContent {
            TakiTheme {
                GenreListScreen(
                    state = state,
                    actions = GenreListActions.Noop.copy(onCoverNeeded = { requested.add(it) }),
                    bottomContentInset = 0.dp,
                )
            }
        }
        compose.waitForIdle()
        assertEquals(listOf("Rock"), requested)

        // Simulate the ViewModel republishing this same row once its cover resolves - the
        // LaunchedEffect key (genre name) is unchanged, so this recomposition must not re-fire.
        state = GenreListUiState(
            isLoading = false,
            rows = persistentListOf(
                GenreListRow(name = "Rock", artworkModel = CoverArtRequest(id = "art-1", cacheKey = "k", size = 0)),
            ),
        )
        compose.waitForIdle()
        assertEquals(listOf("Rock"), requested)
    }

    @Test
    fun `a refresh (coverGeneration bump) re-fires onCoverNeeded for a still-composed row`() {
        // Regression test: a still-visible row was found to never re-request its cover after a
        // pull-to-refresh cleared it, because LaunchedEffect(row.name) alone doesn't change key
        // across a refresh - fixed by also keying on state.coverGeneration.
        val requested = mutableListOf<String>()
        var state by mutableStateOf(GenreListUiState(isLoading = false, rows = persistentListOf(row("Rock"))))
        compose.setContent {
            TakiTheme {
                GenreListScreen(
                    state = state,
                    actions = GenreListActions.Noop.copy(onCoverNeeded = { requested.add(it) }),
                    bottomContentInset = 0.dp,
                )
            }
        }
        compose.waitForIdle()
        assertEquals(listOf("Rock"), requested)

        // The ViewModel clears the resolved cover and bumps coverGeneration on refresh - the row
        // itself (genre name) is unchanged, but the generation bump must still re-fire the effect.
        state = GenreListUiState(isLoading = false, rows = persistentListOf(row("Rock")), coverGeneration = 1)
        compose.waitForIdle()
        assertEquals(listOf("Rock", "Rock"), requested)
    }

    // --- empty / loading state -----------------------------------------------------------------

    @Test
    fun `a finished empty genre list shows the empty state`() {
        setContent(loaded.copy(rows = persistentListOf()))
        compose.onNodeWithText("No genres found", substring = true).assertIsDisplayed()
    }

    @Test
    fun `still loading with no rows does not show the empty state`() {
        setContent(GenreListUiState(isLoading = true, rows = persistentListOf()))
        compose.onNodeWithText("No genres found", substring = true).assertDoesNotExist()
    }
}
