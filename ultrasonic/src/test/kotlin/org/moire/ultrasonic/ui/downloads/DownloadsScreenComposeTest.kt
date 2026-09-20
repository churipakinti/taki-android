/*
 * DownloadsScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.downloads

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Downloads screen (issue #10 phase 4G3): title, one row per downloaded album (title, artist,
 * local song count, trash button), pull-to-refresh, and the empty state. No context menu, queue,
 * progress or confirmation - the legacy screen never had any.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class DownloadsScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, title: String, artist: String? = "Artist", songs: Int = 3) =
        DownloadedAlbumRow(id = id, title = title, artist = artist, songCount = songs)

    private val loaded = DownloadsUiState(
        isLoading = false,
        rows = persistentListOf(
            row("a1", "Abbey Road", "The Beatles", 17),
            row("a2", "Blue", "Joni Mitchell", 1),
        ),
    )

    private fun setContent(
        state: DownloadsUiState,
        actions: DownloadsActions = DownloadsActions.Noop,
    ) {
        compose.setContent {
            TakiTheme { DownloadsScreen(state = state, actions = actions, bottomContentInset = 0.dp) }
        }
    }

    // --- title -----------------------------------------------------------------------------

    @Test
    fun `the Downloads title is drawn exactly once`() {
        setContent(loaded)
        compose.onAllNodesWithText("Downloads").assertCountEquals(1)
    }

    // --- rows ------------------------------------------------------------------------------

    @Test
    fun `each album shows title, artist and a pluralized local song count`() {
        setContent(loaded)
        compose.onNodeWithText("Abbey Road").assertIsDisplayed()
        compose.onNodeWithText("The Beatles").assertIsDisplayed()
        compose.onNodeWithText("17 songs").assertIsDisplayed()
        compose.onNodeWithText("1 song").assertIsDisplayed()
    }

    @Test
    fun `an album without an artist still renders its row`() {
        setContent(loaded.copy(rows = persistentListOf(row("a1", "Untitled", artist = null))))
        compose.onNodeWithText("Untitled").assertIsDisplayed()
    }

    @Test
    fun `tapping a row fires onAlbumClick with that album`() {
        var opened: DownloadedAlbumRow? = null
        setContent(loaded, DownloadsActions.Noop.copy(onAlbumClick = { opened = it }))
        compose.onNodeWithText("Blue").performClick()
        assertEquals("a2", opened?.id)
    }

    @Test
    fun `each row has a remove-download button that fires onRemoveClick for that album only`() {
        var removed: DownloadedAlbumRow? = null
        var opened: DownloadedAlbumRow? = null
        setContent(
            loaded,
            DownloadsActions.Noop.copy(onRemoveClick = { removed = it }, onAlbumClick = { opened = it }),
        )
        val buttons = compose.onAllNodesWithContentDescription("Remove downloaded album")
        buttons.assertCountEquals(2)
        buttons[1].performClick()
        assertEquals("a2", removed?.id)
        assertNull("the trash tap must not also open the album", opened)
    }

    @Test
    fun `a long list scrolls to a late row`() {
        val many = (1..40).map { row("id$it", "Album $it") }
        setContent(loaded.copy(rows = persistentListOf(*many.toTypedArray())))
        compose.onNodeWithTag(DOWNLOADS_CONTENT_TEST_TAG)
            .performScrollToNode(hasText("Album 40"))
        compose.onNodeWithText("Album 40").assertIsDisplayed()
    }

    // --- empty / loading -------------------------------------------------------------------

    @Test
    fun `a finished empty list shows the empty state`() {
        setContent(DownloadsUiState(isLoading = false))
        compose.onNodeWithText("Nothing is downloading").assertIsDisplayed()
    }

    @Test
    fun `still loading with no rows does not show the empty state`() {
        setContent(DownloadsUiState(isLoading = true))
        compose.onNodeWithText("Nothing is downloading").assertDoesNotExist()
    }

    @Test
    fun `the title still shows in the empty state`() {
        setContent(DownloadsUiState(isLoading = false))
        compose.onNodeWithText("Downloads").assertIsDisplayed()
    }
}
