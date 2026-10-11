/*
 * LoadErrorStateComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.albumlist.AlbumListActions
import org.moire.ultrasonic.ui.albumlist.AlbumListRow
import org.moire.ultrasonic.ui.albumlist.AlbumListScreen
import org.moire.ultrasonic.ui.albumlist.AlbumListUiState
import org.moire.ultrasonic.ui.home.HomeActions
import org.moire.ultrasonic.ui.home.HomeScreen
import org.moire.ultrasonic.ui.home.HomeUiState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #31: the shared load-error presentation, and that representative screens pick it (and
 * not the empty state) for a failed load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class LoadErrorStateComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val title = "Couldn't load your music"
    private val retry = "Retry"
    private val empty = "No media found"

    @Test
    fun `the error state shows a message and a Retry that fires once`() {
        var retries = 0
        compose.setContent { TakiTheme { LoadErrorState(onRetry = { retries++ }) } }

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText(retry).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a failed album list shows the error and Retry, never No media found`() {
        var retries = 0
        compose.setContent {
            TakiTheme {
                AlbumListScreen(
                    state = AlbumListUiState(isLoading = false, loadFailed = true),
                    actions = AlbumListActions.Noop.copy(onRefresh = { retries++ }),
                    bottomContentInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(empty).assertDoesNotExist()
        compose.onNodeWithText(retry).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a genuinely empty album list still shows No media found and no Retry`() {
        compose.setContent {
            TakiTheme {
                AlbumListScreen(
                    state = AlbumListUiState(isLoading = false),
                    actions = AlbumListActions.Noop,
                    bottomContentInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.onNodeWithText(retry).assertDoesNotExist()
        compose.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun `a loading album list exposes neither Retry nor the empty message`() {
        compose.setContent {
            TakiTheme {
                AlbumListScreen(
                    state = AlbumListUiState(isLoading = true),
                    actions = AlbumListActions.Noop,
                    bottomContentInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText(retry).assertDoesNotExist()
        compose.onNodeWithText(empty).assertDoesNotExist()
    }

    @Test
    fun `an album list with content never shows the error`() {
        compose.setContent {
            TakiTheme {
                AlbumListScreen(
                    state = AlbumListUiState(
                        isLoading = false,
                        rows = persistentListOf(AlbumListRow("a1", "OK Computer", "Radiohead", null)),
                    ),
                    actions = AlbumListActions.Noop,
                    bottomContentInset = 0.dp,
                )
            }
        }

        compose.onNodeWithText("OK Computer").assertIsDisplayed()
        compose.onNodeWithText(retry).assertDoesNotExist()
    }

    @Test
    fun `a failed Home shows the error and Retry, never No media found`() {
        var retries = 0
        compose.setContent {
            TakiTheme {
                HomeScreen(
                    state = HomeUiState(isLoading = false, loadFailed = true),
                    actions = HomeActions(
                        onRefresh = { retries++ },
                        onOverflow = {},
                        onAlbumClick = {},
                        onOpenPlaylists = {},
                        onOpenAlbums = {},
                        onOpenArtists = {},
                        onOpenSongs = {},
                        onPlayMix = {},
                        onOpenMix = {},
                        onRegenerateMix = {},
                    ),
                )
            }
        }

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(empty).assertDoesNotExist()
        compose.onNodeWithText(retry).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a genuinely empty Home still shows No media found and no Retry`() {
        compose.setContent {
            TakiTheme { HomeScreen(state = HomeUiState(isLoading = false), actions = HomeActions.Noop) }
        }

        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.onNodeWithText(retry).assertDoesNotExist()
    }
}
