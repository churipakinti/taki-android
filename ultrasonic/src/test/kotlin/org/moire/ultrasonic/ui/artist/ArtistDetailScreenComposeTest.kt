/*
 * ArtistDetailScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
 * Artist Detail screen: the centred identity hero, the primary-Play / Radio / Download row, the
 * "Popular" preview, the album + similar shelves, the collapsible biography, and the empty /
 * long-name / no-artwork / font-scale edge cases. JVM / Robolectric, a tall viewport.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2400dp-xxhdpi")
class ArtistDetailScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val loaded = ArtistDetailUiState(
        isLoading = false,
        artistId = "ar1",
        artistName = "Héroes del Silencio",
        albumCount = 2,
        biography = "A Spanish rock band formed in Zaragoza in 1984.",
        albums = persistentListOf(
            ArtistAlbumUi("al1", null, "El Espíritu del Vino", "Héroes del Silencio", null),
            ArtistAlbumUi("al2", null, "Avalancha", "Héroes del Silencio", null),
        ),
        popularTracks = persistentListOf(
            ArtistTrackUi("t1", "1", "Entre dos Tierras", "Senderos de Traición", "5:36", false),
            ArtistTrackUi("t2", "2", "Maldito Duende", "Senderos de Traición", "4:53", false),
        ),
        similarArtists = persistentListOf(
            ArtistSimilarUi("s1", "Los Rodríguez", "cs1", null),
        ),
    )

    private fun setContent(
        state: ArtistDetailUiState,
        actions: ArtistDetailActions = ArtistDetailActions.Noop,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme { ArtistDetailScreen(state = state, actions = actions) }
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(ARTIST_DETAIL_LIST_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    private fun actions(
        onBack: () -> Unit = {},
        onPlay: () -> Unit = {},
        onRadio: () -> Unit = {},
        onDownload: () -> Unit = {},
        onAlbumClick: (ArtistAlbumUi) -> Unit = {},
        onTrackClick: (String) -> Unit = {},
        onSimilarArtistClick: (ArtistSimilarUi) -> Unit = {},
        onRefresh: () -> Unit = {},
    ) = ArtistDetailActions(
        onBack, onPlay, onRadio, onDownload, onAlbumClick, onTrackClick, onSimilarArtistClick, onRefresh,
    )

    @Test
    fun `the hero shows the artist name and the album count`() {
        setContent(loaded)
        // The name is the hero heading (album subtitles legitimately repeat it) - the
        // point is there is no second *title* from a toolbar.
        compose.onNodeWithTag(ARTIST_HERO_NAME_TEST_TAG)
            .assertIsDisplayed()
            .assertTextEquals("Héroes del Silencio")
        compose.onNodeWithText("2 albums", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the lightweight top row has a back affordance that fires onBack`() {
        var backs = 0
        setContent(loaded, actions(onBack = { backs++ }))
        compose.onNodeWithContentDescription("Go back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the primary Play, Radio and Download actions fire`() {
        var plays = 0
        var radios = 0
        var downloads = 0
        setContent(
            loaded,
            actions(onPlay = { plays++ }, onRadio = { radios++ }, onDownload = { downloads++ }),
        )
        compose.onNodeWithContentDescription("Play this artist").performClick()
        compose.onNodeWithContentDescription("Start artist radio").performClick()
        compose.onNodeWithContentDescription("Download this artist").performClick()
        assertEquals(1, plays)
        assertEquals(1, radios)
        assertEquals(1, downloads)
    }

    @Test
    fun `tapping an album opens it`() {
        var opened: ArtistAlbumUi? = null
        setContent(loaded, actions(onAlbumClick = { opened = it }))
        scrollTo("Avalancha").performClick()
        assertEquals("al2", opened?.id)
    }

    @Test
    fun `tapping a popular row plays that track`() {
        var played: String? = null
        setContent(loaded, actions(onTrackClick = { played = it }))
        scrollTo("Maldito Duende").performClick()
        assertEquals("t2", played)
    }

    @Test
    fun `tapping a similar artist opens it`() {
        var opened: ArtistSimilarUi? = null
        setContent(loaded, actions(onSimilarArtistClick = { opened = it }))
        scrollTo("Los Rodríguez").performClick()
        assertEquals("s1", opened?.id)
    }

    @Test
    fun `a long biography can be expanded`() {
        val longBio = "A ".repeat(300)
        setContent(loaded.copy(biography = longBio))
        scrollTo("Show more").performClick()
        compose.onNodeWithText("Show less").assertIsDisplayed()
    }

    @Test
    fun `a finished empty artist shows the no-music state`() {
        setContent(
            loaded.copy(
                albums = persistentListOf(),
                popularTracks = persistentListOf(),
                similarArtists = persistentListOf(),
                biography = null,
                albumCount = 0,
            ),
        )
        compose.onNodeWithText("No music", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a long artist name stays visible`() {
        setContent(
            loaded.copy(artistName = "The Brian Jonestown Massacre and Friends Orchestra Ensemble"),
        )
        compose.onNodeWithTag(ARTIST_HERO_NAME_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `no-artwork still lays out the hero and sections`() {
        setContent(loaded.copy(artworkModel = null))
        compose.onNodeWithTag(ARTIST_HERO_NAME_TEST_TAG).assertIsDisplayed()
        scrollTo("El Espíritu del Vino").assertIsDisplayed()
    }

    @Test
    fun `font scale 1_30 keeps the name and content readable`() {
        setContent(loaded, fontScale = 1.30f)
        compose.onNodeWithTag(ARTIST_HERO_NAME_TEST_TAG).assertIsDisplayed()
        scrollTo("Entre dos Tierras").assertIsDisplayed()
    }
}
