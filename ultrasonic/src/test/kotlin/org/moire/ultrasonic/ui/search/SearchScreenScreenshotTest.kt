/*
 * SearchScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
 * Roborazzi goldens for the Search states. Deterministic fake data; artwork models are left
 * `null` (placeholder). Update with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 * See ultrasonic/src/test/screenshots/README.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h2400dp-xxhdpi")
class SearchScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val recent = SearchUiState(
        recentSearches = persistentListOf("bach", "miles davis", "the beatles", "chopin nocturnes"),
    )

    private val results = SearchUiState(
        query = "bach",
        submitted = true,
        artists = persistentListOf(
            SearchArtistUi("ar1", "Johann Sebastian Bach", isIndex = false),
            SearchArtistUi("ar2", "Wilhelm Friedemann Bach", isIndex = true),
            SearchArtistUi("ar3", "Carl Philipp Emanuel Bach", isIndex = false),
        ),
        albums = persistentListOf(
            SearchAlbumUi("al1", "The Cello Suites", "Yo-Yo Ma", null),
            SearchAlbumUi("al2", "Goldberg Variations", "Glenn Gould", null),
        ),
        songs = persistentListOf(
            SearchSongUi("s1", "Air on the G String", "Johann Sebastian Bach", null),
            SearchSongUi("s2", "Toccata and Fugue in D minor", "Johann Sebastian Bach", null),
        ),
        artistsHaveMore = true,
        albumsHaveMore = true,
        songsHaveMore = true,
    )

    private val noResults = SearchUiState(query = "zzzxyz", submitted = true, isSearching = false)

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
                            .height(2400.dp)
                            .background(TakiTheme.colors.black),
                    ) {
                        content()
                    }
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Test
    fun searchEmpty() = capture("search_empty", widthDp = 412) {
        SearchScreen(state = SearchUiState(), actions = SearchActions.Noop)
    }

    @Test
    fun searchRecent() = capture("search_recent", widthDp = 412) {
        SearchScreen(state = recent, actions = SearchActions.Noop)
    }

    @Test
    fun searchResults() = capture("search_results", widthDp = 412) {
        SearchScreen(state = results, actions = SearchActions.Noop)
    }

    @Test
    fun searchNoResults() = capture("search_no_results", widthDp = 412) {
        SearchScreen(state = noResults, actions = SearchActions.Noop)
    }

    @Test
    fun searchCompact360() = capture("search_compact_360", widthDp = 360) {
        SearchScreen(state = results, actions = SearchActions.Noop)
    }

    @Test
    fun searchFontScale130() = capture("search_font_1_30", widthDp = 412, fontScale = 1.30f) {
        SearchScreen(state = results, actions = SearchActions.Noop)
    }
}
