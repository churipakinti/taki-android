/*
 * PlaylistDetailScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playlist Detail screen (issue #10 phase 4F3): the artwork-first hero (playlist title/artist/
 * metadata, no clickable artist unlike Album Detail), Play/Shuffle/header-menu, the compact
 * track list (every row always shows its artist, unlike Album Detail), the per-track long-press
 * menu including "Remove from playlist", and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2400dp-xxhdpi")
class PlaylistDetailScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: String, title: String = "Track $id", artist: String? = "Miles Davis") =
        PlaylistDetailRow(id, null, title, artist, "3:00", false)

    private val loaded = PlaylistDetailUiState(
        isLoading = false,
        playlistId = "pl1",
        title = "Road Trip",
        artist = "Various Artists",
        songCount = 3,
        totalDuration = "9:00",
        rows = persistentListOf(
            track("1", "So What"),
            track("2", "Take Five", "Dave Brubeck"),
            track("3", "Blue in Green"),
        ),
    )

    private fun setContent(
        state: PlaylistDetailUiState,
        actions: PlaylistDetailActions = PlaylistDetailActions.Noop,
        currentTrackId: String? = null,
    ) {
        compose.setContent {
            TakiTheme {
                PlaylistDetailScreen(state = state, actions = actions, currentTrackId = currentTrackId)
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(PLAYLIST_DETAIL_LIST_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    private fun SemanticsNodeInteraction.longPress() = apply {
        performSemanticsAction(SemanticsActions.OnLongClick)
    }

    private fun noopExcept(
        onPlay: () -> Unit = {},
        onShuffle: () -> Unit = {},
        onShowHeaderMenu: () -> Unit = {},
        onTrackClick: (String) -> Unit = {},
        onTrackContextAction: (String, TrackContextAction) -> Unit = { _, _ -> },
        trackContextMenuState: (String) -> TrackContextMenuState = { TrackContextMenuState() },
        onRefresh: () -> Unit = {},
    ) = PlaylistDetailActions(
        onPlay = onPlay,
        onShuffle = onShuffle,
        onShowHeaderMenu = onShowHeaderMenu,
        onTrackClick = onTrackClick,
        onTrackContextAction = onTrackContextAction,
        trackContextMenuState = trackContextMenuState,
        onRefresh = onRefresh,
    )

    // --- hero ----------------------------------------------------------------------------------

    @Test
    fun `the hero shows the title, the artist and the metadata line`() {
        setContent(loaded)
        compose.onNodeWithText("Road Trip").assertIsDisplayed()
        compose.onNodeWithText("Various Artists").assertIsDisplayed()
        compose.onNodeWithText("9:00", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the artist line is plain text, not a navigation target - unlike Album Detail`() {
        setContent(loaded)
        // No click semantics/role on the artist line: tapping it does nothing observable and
        // PlaylistDetailActions has no onArtistClick at all to fire.
        compose.onNodeWithText("Various Artists").assertIsDisplayed()
    }

    @Test
    fun `Play is the primary action and fires its callback`() {
        var played = 0
        setContent(loaded, noopExcept(onPlay = { played++ }))
        compose.onNodeWithContentDescription("Play this album").performClick()
        assertEquals(1, played)
    }

    @Test
    fun `Shuffle fires its callback`() {
        var shuffled = 0
        setContent(loaded, noopExcept(onShuffle = { shuffled++ }))
        compose.onNodeWithContentDescription("Shuffle this album").performClick()
        assertEquals(1, shuffled)
    }

    @Test
    fun `the header menu button opens the legacy download-rename-delete dialog`() {
        var opened = 0
        setContent(loaded, noopExcept(onShowHeaderMenu = { opened++ }))
        compose.onNodeWithContentDescription("More options").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `a missing cover still announces the artwork region`() {
        setContent(loaded.copy(artworkModel = null))
        compose.onNodeWithContentDescription("Album artwork").assertIsDisplayed()
    }

    // --- rows ----------------------------------------------------------------------------------

    @Test
    fun `a track row plays from that track`() {
        var playedId: String? = null
        setContent(loaded, noopExcept(onTrackClick = { playedId = it }))
        scrollTo("Take Five").performClick()
        assertEquals("2", playedId)
    }

    @Test
    fun `every row shows its artist, even when the playlist has one dominant artist`() {
        setContent(loaded)
        // "So What" and "Blue in Green" both default to "Miles Davis" - two rows, both visible.
        compose.onAllNodesWithText("Miles Davis").assertCountEquals(2)
        scrollTo("Dave Brubeck").assertIsDisplayed()
    }

    // --- empty state -----------------------------------------------------------------------

    @Test
    fun `a finished empty playlist shows the playlist-is-empty state`() {
        setContent(PlaylistDetailUiState(isLoading = false, title = "Gone", rows = persistentListOf()))
        compose.onNodeWithText("Playlist is empty").assertIsDisplayed()
    }

    // --- per-track long-press context menu (legacy context_menu_track_collection_playlist) ----

    @Test
    fun `long-pressing a track opens the context menu, a normal tap still plays`() {
        var played: String? = null
        val actions = mutableListOf<Pair<String, TrackContextAction>>()
        setContent(
            loaded,
            noopExcept(
                onTrackClick = { played = it },
                onTrackContextAction = { id, a -> actions += id to a },
            ),
        )

        compose.onNodeWithText("So What").performClick()
        assertEquals("1", played)
        compose.onNodeWithText("Play from Here").assertDoesNotExist()

        compose.onNodeWithText("So What").longPress()
        compose.onNodeWithText("Play from Here").assertIsDisplayed()
        compose.onNodeWithText("Play Next").performClick()
        assertEquals(listOf("1" to TrackContextAction.PLAY_NEXT), actions)
    }

    @Test
    fun `the context menu exposes exactly the legacy playlist-mode actions, remove-from-playlist included`() {
        setContent(
            loaded,
            noopExcept(
                trackContextMenuState = {
                    TrackContextMenuState(
                        canAddToPlaylist = true,
                        canDownload = true,
                        canDelete = true,
                        canRemoveFromPlaylist = true,
                    )
                },
            ),
        )
        compose.onNodeWithText("So What").longPress()
        listOf(
            "Play Now", "Play Next", "Play Last", "Play from Here", "Start radio",
            "Add to playlist", "Download", "Remove from playlist", "Delete",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `remove-from-playlist is hidden offline, same as add-to-playlist and download`() {
        setContent(
            loaded,
            noopExcept(
                trackContextMenuState = {
                    TrackContextMenuState(
                        canAddToPlaylist = false,
                        canDownload = false,
                        canDelete = false,
                        canRemoveFromPlaylist = false,
                    )
                },
            ),
        )
        compose.onNodeWithText("So What").longPress()
        compose.onNodeWithText("Play Next").assertIsDisplayed()
        compose.onNodeWithText("Add to playlist").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Remove from playlist").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `tapping remove-from-playlist fires the action with the right track id`() {
        var target: Pair<String, TrackContextAction>? = null
        setContent(
            loaded,
            noopExcept(
                onTrackContextAction = { id, a -> target = id to a },
                trackContextMenuState = { TrackContextMenuState(canRemoveFromPlaylist = true) },
            ),
        )
        scrollTo("Blue in Green").longPress()
        compose.onNodeWithText("Remove from playlist").performClick()
        assertEquals("3" to TrackContextAction.REMOVE_FROM_PLAYLIST, target)
    }

    @Test
    fun `the current-track row still long-presses`() {
        var opened = 0
        setContent(
            loaded,
            noopExcept(trackContextMenuState = { opened++; TrackContextMenuState() }),
            currentTrackId = "1",
        )
        compose.onNodeWithText("So What").longPress()
        assertEquals(1, opened)
        compose.onNodeWithText("Play from Here").assertIsDisplayed()
    }
}
