/*
 * LibraryScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library screen: the corrected two-level hierarchy (a 2x2 "Your music" card grid over a
 * quieter "Browse your collection" row list), stable order, and per-destination callbacks.
 * JVM / Robolectric, tall viewport so everything composes and geometry can be asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2400dp-xxhdpi")
class LibraryScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val yourMusic = listOf("Liked Songs", "Liked Albums", "Playlists", "Downloads")
    private val browse = listOf("Albums", "Artists", "Songs", "Genres")

    private fun setContent(
        state: LibraryUiState = LibraryUiState(),
        actions: LibraryActions = LibraryActions.Noop,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme { LibraryScreen(state = state, actions = actions) }
            }
        }
    }

    /** Merged text of every clickable destination (4 cards then the browse rows), tree order. */
    private fun destinationsInOrder(): List<String> =
        compose.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString(separator = " ") { it.text }
            }

    @Test
    fun `renders the title and both section headers`() {
        setContent()
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithText("Your music").assertIsDisplayed()
        compose.onNodeWithText("Browse your collection").assertIsDisplayed()
    }

    @Test
    fun `the four primary cards render in the required stable order`() {
        setContent()
        assertEquals(yourMusic, destinationsInOrder().take(4))
        yourMusic.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `Your music is a two-by-two grid and Browse is a single column`() {
        setContent(LibraryUiState(boxSetsAvailable = true))

        val ls = compose.onNodeWithText("Liked Songs").getUnclippedBoundsInRoot()
        val la = compose.onNodeWithText("Liked Albums").getUnclippedBoundsInRoot()
        val pl = compose.onNodeWithText("Playlists").getUnclippedBoundsInRoot()
        val dl = compose.onNodeWithText("Downloads").getUnclippedBoundsInRoot()

        // Top card row: Liked Songs | Liked Albums, same top, second one to the right.
        assertEquals(ls.top.value, la.top.value, ALIGN_TOLERANCE_DP)
        assertTrue("Liked Albums should sit right of Liked Songs", la.left > ls.left)
        // Bottom card row: Playlists | Downloads, below the top row, same layout.
        assertTrue("Playlists should sit below the top card row", pl.top > ls.top)
        assertEquals(pl.top.value, dl.top.value, ALIGN_TOLERANCE_DP)
        assertTrue("Downloads should sit right of Playlists", dl.left > pl.left)
        // Cards are half-width; a browse row is full-width (wider than one card).
        val albums = compose.onNodeWithText("Albums").getUnclippedBoundsInRoot()
        assertTrue(
            "A browse row is wider than one card",
            (albums.right - albums.left) > (ls.right - ls.left),
        )
        // Browse rows are a single stacked column.
        val artists = compose.onNodeWithText("Artists").getUnclippedBoundsInRoot()
        assertEquals(albums.left.value, artists.left.value, ALIGN_TOLERANCE_DP)
        assertTrue("Artists is stacked below Albums", artists.top > albums.top)
    }

    @Test
    fun `the browse rows render in the required stable order`() {
        setContent(LibraryUiState(boxSetsAvailable = true))
        assertEquals(browse + "Box Sets", destinationsInOrder().drop(4))
    }

    @Test
    fun `hides Box Sets when unavailable`() {
        setContent(LibraryUiState(boxSetsAvailable = false))
        assertEquals(yourMusic + browse, destinationsInOrder())
        compose.onNodeWithText("Box Sets").assertDoesNotExist()
    }

    @Test
    fun `every destination invokes its own callback`() {
        val fired = mutableListOf<String>()
        setContent(
            state = LibraryUiState(boxSetsAvailable = true),
            actions = LibraryActions(
                onOverflow = { fired += "overflow" },
                onLikedSongs = { fired += "Liked Songs" },
                onLikedAlbums = { fired += "Liked Albums" },
                onPlaylists = { fired += "Playlists" },
                onDownloads = { fired += "Downloads" },
                onAlbums = { fired += "Albums" },
                onArtists = { fired += "Artists" },
                onSongs = { fired += "Songs" },
                onGenres = { fired += "Genres" },
                onBoxSets = { fired += "Box Sets" },
            ),
        )

        val expected = yourMusic + browse + "Box Sets"
        expected.forEach { label -> compose.onNodeWithText(label).performClick() }

        assertEquals(expected, fired)
    }

    @Test
    fun `the overflow control opens the library hub`() {
        var overflow = 0
        setContent(actions = noopExcept(onOverflow = { overflow++ }))
        compose.onNodeWithContentDescription("Your library").performClick()
        assertEquals(1, overflow)
    }

    @Test
    fun `font scale 1_30 keeps every destination present`() {
        setContent(state = LibraryUiState(boxSetsAvailable = true), fontScale = 1.30f)
        (yourMusic + browse + "Box Sets").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    private companion object {
        const val ALIGN_TOLERANCE_DP = 0.5f
    }

    private fun noopExcept(onOverflow: () -> Unit = {}) = LibraryActions(
        onOverflow = onOverflow,
        onLikedSongs = {},
        onLikedAlbums = {},
        onPlaylists = {},
        onDownloads = {},
        onAlbums = {},
        onArtists = {},
        onSongs = {},
        onGenres = {},
        onBoxSets = {},
    )
}
