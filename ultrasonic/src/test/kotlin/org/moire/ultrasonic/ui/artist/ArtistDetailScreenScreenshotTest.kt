/*
 * ArtistDetailScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Artist Detail. Fully deterministic - fake [ArtistDetailUiState], no
 * artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h2400dp-xxhdpi")
class ArtistDetailScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun album(id: String, title: String) =
        ArtistAlbumUi(id, null, title, "Héroes del Silencio", null)

    private fun track(id: String, rank: Int, title: String, dur: String) =
        ArtistTrackUi(id, rank.toString(), title, "Senderos de Traición", dur, false)

    private val standard = ArtistDetailUiState(
        isLoading = false,
        artistId = "ar1",
        artistName = "Héroes del Silencio",
        albumCount = 3,
        biography = "Héroes del Silencio were a Spanish rock band formed in Zaragoza in 1984 " +
            "by Enrique Bunbury and Juan Valdivia.",
        albums = persistentListOf(
            album("a1", "El Espíritu del Vino"),
            album("a2", "Senderos de Traición"),
            album("a3", "Avalancha"),
        ),
        popularTracks = persistentListOf(
            track("t1", 1, "Entre dos Tierras", "5:36"),
            track("t2", 2, "Maldito Duende", "4:53"),
            track("t3", 3, "La Chispa Adecuada", "5:07"),
        ),
        similarArtists = persistentListOf(
            ArtistSimilarUi("s1", "Los Rodríguez", "cs1", null),
            ArtistSimilarUi("s2", "Extremoduro", "cs2", null),
        ),
    )

    private fun capture(
        tag: String,
        widthDp: Int,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme {
                    Box(
                        modifier = Modifier
                            .testTag(tag)
                            .width(widthDp.dp)
                            .heightIn(max = 2400.dp)
                            .background(TakiTheme.colors.black),
                    ) {
                        content()
                    }
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: ArtistDetailUiState): @Composable () -> Unit =
        { ArtistDetailScreen(state = state, actions = ArtistDetailActions.Noop) }

    @Test
    fun artistStandard() = capture("artist_standard", widthDp = 412) { screen(standard)() }

    @Test
    fun artistLongName() = capture("artist_long_name", widthDp = 412) {
        screen(
            standard.copy(
                artistName = "The Brian Jonestown Massacre and Friends Orchestra Ensemble",
            ),
        )()
    }

    @Test
    fun artistNoArtwork() = capture("artist_no_artwork", widthDp = 412) {
        screen(standard.copy(artworkModel = null))()
    }

    @Test
    fun artistOnlyAlbums() = capture("artist_only_albums", widthDp = 412) {
        screen(
            standard.copy(
                popularTracks = persistentListOf(),
                biography = null,
                similarArtists = persistentListOf(),
            ),
        )()
    }

    @Test
    fun artistEmpty() = capture("artist_empty", widthDp = 412) {
        screen(
            standard.copy(
                albums = persistentListOf(),
                popularTracks = persistentListOf(),
                biography = null,
                similarArtists = persistentListOf(),
                albumCount = 0,
            ),
        )()
    }

    @Test
    fun artistCompact360() = capture("artist_compact_360", widthDp = 360) { screen(standard)() }

    @Test
    fun artistFontScale130() = capture("artist_font_1_30", widthDp = 412, fontScale = 1.30f) {
        screen(standard)()
    }
}
