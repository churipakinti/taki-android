/*
 * AlbumDetailScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Album Detail screen: the artwork-first hero, the obvious Play primary with the rest
 * receding, the compact track list with quiet disc markers, the issue #15 heart, the issue
 * #16 artist + overflow navigation, and the empty / long-title / missing-art edge cases.
 * JVM / Robolectric, a tall viewport so the whole composition lays out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2400dp-xxhdpi")
class AlbumDetailScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: String, title: String = "Track $id") =
        AlbumDetailRow.Track(id, null, title, null, "3:00", false)

    private val loaded = AlbumDetailUiState(
        isLoading = false,
        albumId = "al1",
        title = "Kind of Blue",
        artist = "Miles Davis",
        artistId = "ar1",
        year = "1959",
        genre = "Jazz",
        songCount = 3,
        totalDuration = "45:44",
        starVisible = true,
        rows = persistentListOf(
            track("1", "So What"),
            track("2", "Freddie Freeloader"),
            track("3", "Blue in Green"),
        ),
    )

    private fun setContent(
        state: AlbumDetailUiState,
        actions: AlbumDetailActions = AlbumDetailActions.Noop,
        currentTrackId: String? = null,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme {
                    AlbumDetailScreen(
                        state = state,
                        actions = actions,
                        currentTrackId = currentTrackId,
                    )
                }
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(ALBUM_DETAIL_LIST_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    /**
     * Fire the row's long-press via its semantics action - `combinedClickable` registers
     * [SemanticsActions.OnLongClick], and invoking it directly is deterministic under
     * Robolectric (unlike a timed touch gesture).
     */
    private fun SemanticsNodeInteraction.longPress() = apply {
        performSemanticsAction(SemanticsActions.OnLongClick)
    }

    @Test
    fun `the hero shows the title, the artist and the metadata line`() {
        setContent(loaded)
        compose.onNodeWithText("Kind of Blue").assertIsDisplayed()
        compose.onNodeWithText("Miles Davis").assertIsDisplayed()
        compose.onNodeWithText("1959", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Jazz", substring = true).assertIsDisplayed()
    }

    @Test
    fun `Play is the primary action and fires its callback`() {
        var played = 0
        setContent(loaded, noopExcept(onPlay = { played++ }))
        compose.onNodeWithContentDescription("Play this album").performClick()
        assertEquals(1, played)
    }

    @Test
    fun `Shuffle and Download fire their callbacks`() {
        var shuffled = 0
        var downloaded = 0
        setContent(loaded, noopExcept(onShuffle = { shuffled++ }, onDownload = { downloaded++ }))
        compose.onNodeWithContentDescription("Shuffle this album").performClick()
        compose.onNodeWithContentDescription("Download this album").performClick()
        assertEquals(1, shuffled)
        assertEquals(1, downloaded)
    }

    @Test
    fun `the heart shows only for the id3 album mode and toggles state`() {
        var toggledTo: Boolean? = null
        setContent(loaded.copy(isStarred = false), noopExcept(onToggleStar = { toggledTo = it }))
        compose.onNodeWithContentDescription("Like this album").performClick()
        assertEquals(true, toggledTo)
    }

    @Test
    fun `the heart is absent when it is not an id3 album`() {
        setContent(loaded.copy(starVisible = false))
        compose.onNodeWithContentDescription("Like this album").assertDoesNotExist()
    }

    @Test
    fun `a starred album shows the unlike affordance`() {
        setContent(loaded.copy(isStarred = true))
        compose.onNodeWithContentDescription("Unlike this album").assertIsDisplayed()
    }

    @Test
    fun `the Information action appears only once notes are present`() {
        setContent(loaded)
        compose.onNodeWithContentDescription("Album information").assertDoesNotExist()
    }

    @Test
    fun `the Information action fires once notes are present`() {
        var infoOpened = 0
        setContent(
            loaded.copy(notes = "Recorded over two sessions in 1959."),
            noopExcept(onShowInfo = { infoOpened++ }),
        )
        compose.onNodeWithContentDescription("Album information").performClick()
        assertEquals(1, infoOpened)
    }

    @Test
    fun `the overflow lists the issue 16 actions and routes them`() {
        val fired = mutableListOf<AlbumOverflowItem>()
        setContent(loaded, noopExcept(onOverflowItem = { fired += it }))

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Go to artist").performClick()

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Play Next").performClick()

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Start radio").performClick()

        assertEquals(
            listOf(
                AlbumOverflowItem.GO_TO_ARTIST,
                AlbumOverflowItem.PLAY_NEXT,
                AlbumOverflowItem.START_RADIO,
            ),
            fired,
        )
    }

    @Test
    fun `Go to artist is hidden with no single artist, Start radio is hidden offline`() {
        setContent(loaded.copy(artistId = null, radioAvailable = false))
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Go to artist").assertDoesNotExist()
        compose.onNodeWithText("Start radio").assertDoesNotExist()
        compose.onNodeWithText("Play Next").assertIsDisplayed()
    }

    @Test
    fun `tapping the artist line navigates when the album has one artist`() {
        var artistTaps = 0
        setContent(loaded, noopExcept(onArtistClick = { artistTaps++ }))
        compose.onNodeWithText("Miles Davis").performClick()
        assertEquals(1, artistTaps)
    }

    @Test
    fun `a track row plays from that track`() {
        var playedId: String? = null
        setContent(loaded, noopExcept(onTrackClick = { playedId = it }))
        scrollTo("Freddie Freeloader").performClick()
        assertEquals("2", playedId)
    }

    @Test
    fun `a multi-disc album renders quiet disc headers that play their disc`() {
        var discPlayed: Int? = null
        val multi = loaded.copy(
            hasMultipleDiscs = true,
            rows = persistentListOf(
                AlbumDetailRow.Disc(1),
                track("1", "Opening"),
                AlbumDetailRow.Disc(2),
                track("2", "Closing"),
            ),
        )
        setContent(multi, noopExcept(onDiscPlay = { discPlayed = it }))
        scrollTo("Disc 2").assertIsDisplayed()
        compose.onNodeWithTag(ALBUM_DETAIL_LIST_TEST_TAG).performScrollToNode(hasText("Disc 1"))
        compose.onAllNodesWithContentDescription("Play this disc")[0].performClick()
        assertEquals(1, discPlayed)
    }

    @Test
    fun `a long classical work title stays visible instead of being clipped away`() {
        val long = loaded.copy(
            title = "Concerto for 2 Violins in D minor, BWV 1043 - II. Largo ma non tanto",
            rows = persistentListOf(
                track("1", "I. Vivace"),
                track("2", "III. Allegro assai, molto vivace e sempre con brio giocoso"),
            ),
        )
        setContent(long)
        compose.onNodeWithText("BWV 1043", substring = true).assertIsDisplayed()
        scrollTo("molto vivace").assertIsDisplayed()
    }

    @Test
    fun `a missing cover still announces the artwork region`() {
        setContent(loaded.copy(artworkModel = null))
        compose.onNodeWithContentDescription("Album artwork").assertIsDisplayed()
    }

    @Test
    fun `a finished empty album shows the no-media state`() {
        setContent(AlbumDetailUiState(isLoading = false, title = "Gone", rows = persistentListOf()))
        compose.onNodeWithText("No media found").assertIsDisplayed()
    }

    @Test
    fun `font scale 1_30 keeps the title and the tracks usable`() {
        setContent(loaded, fontScale = 1.30f)
        compose.onNodeWithText("Kind of Blue").assertIsDisplayed()
        scrollTo("So What").assertIsDisplayed()
    }

    // --- per-track long-press context menu (legacy context_menu_track_collection parity) ----

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

        // Normal tap = play from that row.
        compose.onNodeWithText("So What").performClick()
        assertEquals("1", played)
        // ...and it did not also open a menu.
        compose.onNodeWithText("Play from Here").assertDoesNotExist()

        compose.onNodeWithText("Freddie Freeloader").longPress()
        compose.onNodeWithText("Play from Here").assertIsDisplayed()
        compose.onNodeWithText("Play Next").performClick()
        assertEquals(listOf("2" to TrackContextAction.PLAY_NEXT), actions)
    }

    @Test
    fun `the context menu exposes exactly the legacy album-mode actions`() {
        setContent(
            loaded,
            noopExcept(
                trackContextMenuState = {
                    TrackContextMenuState(
                        canAddToPlaylist = true,
                        canDownload = true,
                        canDelete = true,
                    )
                },
            ),
        )
        compose.onNodeWithText("So What").longPress()
        listOf(
            "Play Now", "Play Next", "Play Last", "Play from Here",
            "Start radio", "Add to playlist", "Download", "Delete",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `download and delete visibility follow the resolved menu state`() {
        setContent(
            loaded,
            noopExcept(
                trackContextMenuState = {
                    TrackContextMenuState(
                        canAddToPlaylist = false,
                        canDownload = false,
                        canDelete = false,
                    )
                },
            ),
        )
        compose.onNodeWithText("So What").longPress()
        compose.onNodeWithText("Play Next").assertIsDisplayed()
        compose.onNodeWithText("Add to playlist").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `long-pressing routes the correct track id`() {
        var target: Pair<String, TrackContextAction>? = null
        setContent(loaded, noopExcept(onTrackContextAction = { id, a -> target = id to a }))
        scrollTo("Blue in Green").longPress()
        compose.onNodeWithText("Download").performClick()
        assertEquals("3" to TrackContextAction.DOWNLOAD, target)
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

    @Test
    fun `a multi-disc row long-presses like any other`() {
        var target: Pair<String, TrackContextAction>? = null
        val multi = loaded.copy(
            hasMultipleDiscs = true,
            rows = persistentListOf(
                AlbumDetailRow.Disc(1),
                track("1", "Opening"),
                AlbumDetailRow.Disc(2),
                track("2", "Closing"),
            ),
        )
        setContent(multi, noopExcept(onTrackContextAction = { id, a -> target = id to a }))
        scrollTo("Closing").longPress()
        compose.onNodeWithText("Play Last").performClick()
        assertEquals("2" to TrackContextAction.PLAY_LAST, target)
    }

    private fun noopExcept(
        onPlay: () -> Unit = {},
        onShuffle: () -> Unit = {},
        onToggleStar: (Boolean) -> Unit = {},
        onDownload: () -> Unit = {},
        onShowInfo: () -> Unit = {},
        onArtistClick: () -> Unit = {},
        onOverflowItem: (AlbumOverflowItem) -> Unit = {},
        onTrackClick: (String) -> Unit = {},
        onTrackContextAction: (String, TrackContextAction) -> Unit = { _, _ -> },
        trackContextMenuState: (String) -> TrackContextMenuState = { TrackContextMenuState() },
        onDiscPlay: (Int) -> Unit = {},
        onRefresh: () -> Unit = {},
    ) = AlbumDetailActions(
        onPlay = onPlay,
        onShuffle = onShuffle,
        onToggleStar = onToggleStar,
        onDownload = onDownload,
        onShowInfo = onShowInfo,
        onArtistClick = onArtistClick,
        onOverflowItem = onOverflowItem,
        onTrackClick = onTrackClick,
        onTrackContextAction = onTrackContextAction,
        trackContextMenuState = trackContextMenuState,
        onDiscPlay = onDiscPlay,
        onDiscDownload = {},
        onRefresh = onRefresh,
    )
}
