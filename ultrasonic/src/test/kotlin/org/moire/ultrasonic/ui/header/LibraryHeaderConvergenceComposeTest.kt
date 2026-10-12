/*
 * LibraryHeaderConvergenceComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.header

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.albumlist.AlbumListActions
import org.moire.ultrasonic.ui.albumlist.AlbumListScreen
import org.moire.ultrasonic.ui.albumlist.AlbumListUiState
import org.moire.ultrasonic.ui.artistlist.ArtistListActions
import org.moire.ultrasonic.ui.artistlist.ArtistListScreen
import org.moire.ultrasonic.ui.artistlist.ArtistListUiState
import org.moire.ultrasonic.ui.downloads.DownloadsActions
import org.moire.ultrasonic.ui.downloads.DownloadsScreen
import org.moire.ultrasonic.ui.downloads.DownloadsUiState
import org.moire.ultrasonic.ui.genrelist.GenreListActions
import org.moire.ultrasonic.ui.genrelist.GenreListScreen
import org.moire.ultrasonic.ui.genrelist.GenreListUiState
import org.moire.ultrasonic.ui.playlistlist.PlaylistListActions
import org.moire.ultrasonic.ui.playlistlist.PlaylistListScreen
import org.moire.ultrasonic.ui.playlistlist.PlaylistListUiState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.ui.tracklist.TrackListActions
import org.moire.ultrasonic.ui.tracklist.TrackListScreen
import org.moire.ultrasonic.ui.tracklist.TrackListUiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #26 phase 5C.4: every standard Library destination owns one Compose header - exactly one
 * back arrow and exactly one title - and the arrow dispatches the host's `onBack` callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h900dp-xxhdpi")
class LibraryHeaderConvergenceComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val backLabel: String
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.common_navigate_back)

    private fun checkHeader(title: String, content: @androidx.compose.runtime.Composable (onBack: () -> Unit) -> Unit) {
        var backs = 0
        compose.setContent { TakiTheme { content { backs++ } } }
        compose.onAllNodesWithContentDescription(backLabel).assertCountEquals(1)
        compose.onAllNodesWithText(title).assertCountEquals(1)
        compose.onNodeWithContentDescription(backLabel).performClick()
        assertEquals("the header arrow must call the host's onBack once", 1, backs)
    }

    @Test
    fun albums() = checkHeader("Albums") { back ->
        AlbumListScreen(AlbumListUiState(isLoading = false), AlbumListActions.Noop.copy(onBack = back), 0.dp, title = "Albums")
    }

    @Test
    fun albumsKeepContextualTitle() = checkHeader("Albums by Daft Punk") { back ->
        AlbumListScreen(
            AlbumListUiState(isLoading = false),
            AlbumListActions.Noop.copy(onBack = back),
            0.dp,
            title = "Albums by Daft Punk",
        )
    }

    @Test
    fun artists() = checkHeader("Artists") { back ->
        ArtistListScreen(ArtistListUiState(isLoading = false), ArtistListActions.Noop.copy(onBack = back), 0.dp, title = "Artists")
    }

    @Test
    fun genres() = checkHeader("Genres") { back ->
        GenreListScreen(GenreListUiState(isLoading = false), GenreListActions.Noop.copy(onBack = back), 0.dp, title = "Genres")
    }

    @Test
    fun playlists() = checkHeader("Playlists") { back ->
        PlaylistListScreen(PlaylistListUiState(isLoading = false), PlaylistListActions.Noop.copy(onBack = back), 0.dp, title = "Playlists")
    }

    @Test
    fun songs() = checkHeader("Songs") { back ->
        TrackListScreen(TrackListUiState(isLoading = false, headerTitle = "Songs"), TrackListActions.Noop.copy(onBack = back), 0.dp)
    }

    @Test
    fun likedSongs() = checkHeader("Liked Songs") { back ->
        TrackListScreen(
            TrackListUiState(isLoading = false, headerTitle = "Liked Songs"),
            TrackListActions.Noop.copy(onBack = back),
            0.dp,
        )
    }

    @Test
    fun downloads() = checkHeader("Downloads") { back ->
        DownloadsScreen(DownloadsUiState(isLoading = false), DownloadsActions.Noop.copy(onBack = back), 0.dp)
    }

    @Test
    fun withoutATitleThereIsNoHeader() {
        compose.setContent {
            TakiTheme { AlbumListScreen(AlbumListUiState(isLoading = false), AlbumListActions.Noop, 0.dp) }
        }
        compose.onAllNodesWithContentDescription(backLabel).assertCountEquals(0)
    }
}
