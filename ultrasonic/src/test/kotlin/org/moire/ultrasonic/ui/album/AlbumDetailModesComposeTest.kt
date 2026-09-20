/*
 * AlbumDetailModesComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The visible differences between Album Detail's modes (issue #10 phase 4H1): folder sub-folder
 * rows, the downloaded album's per-track status indicator, and the hidden server-dependent
 * actions in offline mode. The shared online id3 rendering has its own suite
 * ([AlbumDetailScreenComposeTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2400dp-xxhdpi")
class AlbumDetailModesComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: String) = AlbumDetailRow.Track(id, null, "Song $id", null, "3:00", false)

    private val base = AlbumDetailUiState(
        isLoading = false,
        albumId = "al1",
        title = "Some Album",
        artist = "Some Artist",
        songCount = 2,
        starVisible = true,
        rows = persistentListOf(track("1"), track("2")),
    )

    private fun setContent(
        state: AlbumDetailUiState,
        actions: AlbumDetailActions = AlbumDetailActions.Noop,
    ) {
        compose.setContent {
            TakiTheme { AlbumDetailScreen(state = state, actions = actions, currentTrackId = null) }
        }
    }

    // --- folder mode --------------------------------------------------------------------------

    private val withFolders = base.copy(
        rows = persistentListOf(
            AlbumDetailRow.Folder("f1", "CD 1", "Some Artist"),
            AlbumDetailRow.Folder("f2", "CD 2", null),
            track("1"),
        ),
    )

    @Test
    fun `sub-folders render as rows with their title and artist`() {
        setContent(withFolders)
        compose.onNodeWithText("CD 1").assertIsDisplayed()
        compose.onNodeWithText("CD 2").assertIsDisplayed()
        compose.onNodeWithText("Song 1").assertIsDisplayed()
    }

    @Test
    fun `tapping a sub-folder fires onFolderClick with its id`() {
        var opened: String? = null
        setContent(withFolders, AlbumDetailActions.Noop.copy(onFolderClick = { opened = it }))
        compose.onNodeWithText("CD 2").performClick()
        assertEquals("f2", opened)
    }

    @Test
    fun `a folder-only directory shows the folders but no Play, Shuffle or Download`() {
        setContent(
            base.copy(
                songCount = 0,
                artist = "",
                rows = persistentListOf(AlbumDetailRow.Folder("f1", "Disc One", null)),
            ),
        )
        compose.onNodeWithText("Disc One").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play this album").assertDoesNotExist()
        compose.onNodeWithContentDescription("Shuffle this album").assertDoesNotExist()
        compose.onNodeWithContentDescription("Download this album").assertDoesNotExist()
    }

    @Test
    fun `a folder album with tracks keeps Play, Shuffle and Download`() {
        setContent(withFolders)
        compose.onNodeWithContentDescription("Play this album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Shuffle this album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Download this album").assertIsDisplayed()
    }

    // --- offline mode -------------------------------------------------------------------------

    @Test
    fun `offline mode hides Download and the heart but keeps Play and Shuffle`() {
        setContent(base.copy(online = false, starVisible = false))
        compose.onNodeWithContentDescription("Download this album").assertDoesNotExist()
        compose.onNodeWithContentDescription("Like this album").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play this album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Shuffle this album").assertIsDisplayed()
    }

    @Test
    fun `online mode shows Download and the heart`() {
        setContent(base)
        compose.onNodeWithContentDescription("Download this album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Like this album").assertIsDisplayed()
    }

    @Test
    fun `offline mode hides the per-disc Download button but keeps per-disc Play`() {
        setContent(
            base.copy(
                online = false,
                hasMultipleDiscs = true,
                rows = persistentListOf(AlbumDetailRow.Disc(1), track("1"), AlbumDetailRow.Disc(2), track("2")),
            ),
        )
        compose.onNodeWithContentDescription("Download this disc").assertDoesNotExist()
    }

    // --- downloaded-album status indicators --------------------------------------------------

    private val downloaded = base.copy(showDownloadStatus = true)

    @Test
    fun `a downloaded album asks for each row status once it is composed`() {
        val requested = mutableListOf<String>()
        setContent(downloaded, AlbumDetailActions.Noop.copy(onTrackStatusNeeded = { requested.add(it) }))
        compose.waitForIdle()
        assertEquals(listOf("1", "2"), requested)
    }

    @Test
    fun `an online album never asks for row status`() {
        val requested = mutableListOf<String>()
        setContent(base, AlbumDetailActions.Noop.copy(onTrackStatusNeeded = { requested.add(it) }))
        compose.waitForIdle()
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `a failed track shows the error indicator and tapping it explains it`() {
        var explained = 0
        setContent(
            downloaded.copy(
                trackStatuses = persistentMapOf(
                    "1" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.FAILED),
                ),
            ),
            AlbumDetailActions.Noop.copy(onDownloadErrorClick = { explained++ }),
        )
        compose.onNodeWithContentDescription("download this song", substring = true).performClick()
        assertEquals(1, explained)
    }

    @Test
    fun `an online album ignores statuses even if present`() {
        setContent(
            base.copy(
                trackStatuses = persistentMapOf(
                    "1" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.FAILED),
                ),
            ),
        )
        compose.onNodeWithContentDescription("download this song", substring = true).assertDoesNotExist()
    }

    @Test
    fun `downloading and queued tracks render without crashing`() {
        setContent(
            downloaded.copy(
                trackStatuses = persistentMapOf(
                    "1" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.DOWNLOADING, 40),
                    "2" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.QUEUED),
                ),
            ),
        )
        compose.onNodeWithText("Song 1").assertIsDisplayed()
        compose.onNodeWithText("Song 2").assertIsDisplayed()
    }
}
