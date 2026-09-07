/*
 * HomeScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home screen interactions and state rendering (JVM / Robolectric). Deterministic fake data,
 * artwork models left `null` so the placeholder renders. A deliberately short viewport so
 * "reachable by scrolling" is a real assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h640dp-xxhdpi")
class HomeScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun album(id: String, title: String) =
        HomeAlbumUi(id, title, "Artist $id", null, true, "parent-$id")

    private val loadedState = HomeUiState(
        greeting = HomeGreeting.EVENING,
        isLoading = false,
        featuredMix = FeaturedMixUi(trackCount = 25, artworkModel = null),
        recentlyPlayed = persistentListOf(album("r1", "Recent One"), album("r2", "Recent Two")),
        shelves = persistentListOf(
            HomeShelfUi(HomeShelfKind.LIKED, persistentListOf(album("l1", "Liked One"))),
            HomeShelfUi(HomeShelfKind.NEWEST, persistentListOf(album("n1", "Newest One"))),
        ),
    )

    private fun setContent(state: HomeUiState, actions: HomeActions) {
        compose.setContent { TakiTheme { HomeScreen(state = state, actions = actions) } }
    }

    /** Scroll the Home list until [matcher] is composed, then return that node. */
    private fun scrollTo(matcher: SemanticsMatcher) = run {
        compose.onNodeWithTag(HOME_LIST_TEST_TAG).performScrollToNode(matcher)
        compose.onNode(matcher)
    }

    @Test
    fun `renders the greeting, the featured mix and every shelf`() {
        setContent(loadedState, HomeActions.Noop)

        compose.onNodeWithText("Good evening").assertIsDisplayed()
        compose.onNodeWithText("Daily Mix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play Daily Mix").assertIsDisplayed()

        scrollTo(hasText("Recently Played")).assertIsDisplayed()
        scrollTo(hasText("Liked Albums")).assertIsDisplayed()
        scrollTo(hasText("Recently Added")).assertIsDisplayed()
        scrollTo(hasText("Newest One", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `the play button issues the mix playback command`() {
        var played = 0
        setContent(loadedState, noopExcept(onPlayMix = { played++ }))

        compose.onNodeWithContentDescription("Play Daily Mix").performClick()

        assertEquals(1, played)
    }

    @Test
    fun `the featured card still works with the atmospheric wash enabled`() {
        var played = 0
        val withArtwork = loadedState.copy(
            featuredMix = FeaturedMixUi(
                trackCount = 25,
                artworkModel = CoverArtRequest("cover-x", "key-x", size = 0),
            ),
        )
        setContent(withArtwork, noopExcept(onPlayMix = { played++ }))

        compose.onNodeWithText("Daily Mix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play Daily Mix")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, played)
    }

    @Test
    fun `tapping an album forwards it to the navigation callback`() {
        var clicked: HomeAlbumUi? = null
        setContent(loadedState, noopExcept(onAlbumClick = { clicked = it }))

        compose.onNodeWithText("Recent One", substring = true).performClick()

        assertEquals("r1", clicked?.id)
        assertEquals("parent-r1", clicked?.parentId)
    }

    @Test
    fun `the overflow control opens the library hub`() {
        var overflow = 0
        setContent(loadedState, noopExcept(onOverflow = { overflow++ }))

        compose.onNodeWithContentDescription("Your library").performClick()

        assertEquals(1, overflow)
    }

    @Test
    fun `a quick-access chip still navigates`() {
        var albums = 0
        setContent(loadedState, noopExcept(onOpenAlbums = { albums++ }))

        compose.onNodeWithText("Albums").assertHasClickAction().performClick()

        assertEquals(1, albums)
    }

    @Test
    fun `the empty state replaces the content when every source is empty`() {
        setContent(HomeUiState(isLoading = false), HomeActions.Noop)

        compose.onNodeWithText("No media found").assertIsDisplayed()
        compose.onNodeWithText("Recently Played").assertDoesNotExist()
    }

    @Test
    fun `the cold-load skeleton shows the header and no empty message`() {
        setContent(HomeUiState(isLoading = true), HomeActions.Noop)

        compose.onNodeWithText("Good morning").assertIsDisplayed()
        compose.onNodeWithText("No media found").assertDoesNotExist()
    }

    private fun noopExcept(
        onOverflow: () -> Unit = {},
        onAlbumClick: (HomeAlbumUi) -> Unit = {},
        onOpenAlbums: () -> Unit = {},
        onPlayMix: () -> Unit = {},
    ) = HomeActions(
        onRefresh = {},
        onOverflow = onOverflow,
        onAlbumClick = onAlbumClick,
        onOpenPlaylists = {},
        onOpenAlbums = onOpenAlbums,
        onOpenArtists = {},
        onOpenSongs = {},
        onPlayMix = onPlayMix,
        onOpenMix = {},
        onRegenerateMix = {},
    )
}
