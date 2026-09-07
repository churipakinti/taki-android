/*
 * HomeScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

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
 * Roborazzi goldens for the canonical Home states. Deterministic fake data, artwork left
 * `null` (placeholder). Update with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 * See ultrasonic/src/test/screenshots/README.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h2400dp-xxhdpi")
class HomeScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun album(id: String, title: String, artist: String) =
        HomeAlbumUi(id, title, artist, null, true, null)

    private val loaded = HomeUiState(
        greeting = HomeGreeting.MORNING,
        isLoading = false,
        featuredMix = FeaturedMixUi(trackCount = 25, artworkModel = null),
        recentlyPlayed = persistentListOf(
            album("r1", "Blue Train", "John Coltrane"),
            album("r2", "Mingus Ah Um", "Charles Mingus"),
            album("r3", "The Shape of Jazz to Come", "Ornette Coleman"),
        ),
        shelves = persistentListOf(
            HomeShelfUi(
                HomeShelfKind.LIKED,
                persistentListOf(
                    album("l1", "A Love Supreme", "John Coltrane"),
                    album("l2", "Saxophone Colossus", "Sonny Rollins"),
                ),
            ),
            HomeShelfUi(
                HomeShelfKind.NEWEST,
                persistentListOf(album("n1", "Speak No Evil", "Wayne Shorter")),
            ),
        ),
    )

    private val longText = HomeUiState(
        greeting = HomeGreeting.EVENING,
        isLoading = false,
        featuredMix = FeaturedMixUi(trackCount = 7, artworkModel = null),
        recentlyPlayed = persistentListOf(
            album(
                "x1",
                "An Extraordinarily Long Album Title That Really Ought To Ellipsize Cleanly",
                "A Collaborative Ensemble With An Equally Unreasonable Name",
            ),
            album("x2", "Short", "Someone"),
        ),
        shelves = persistentListOf(
            HomeShelfUi(
                HomeShelfKind.FREQUENT,
                persistentListOf(
                    album(
                        "x3",
                        "Another Title Of Excessive Length For The Compact Shelf Card Layout",
                        "Artist Whose Name Also Does Not Fit On One Line At All",
                    ),
                ),
            ),
        ),
    )

    private fun capture(tag: String, widthDp: Int, fontScale: Float = 1f, content: @Composable () -> Unit) {
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

    @Test
    fun homeStandard412() = capture("home_standard_412", widthDp = 412) {
        HomeScreen(state = loaded, actions = HomeActions.Noop)
    }

    @Test
    fun homeCompact360() = capture("home_compact_360", widthDp = 360) {
        HomeScreen(state = loaded, actions = HomeActions.Noop)
    }

    @Test
    fun homeLongTextFontScale130() =
        capture("home_long_text_font_1_30", widthDp = 412, fontScale = 1.30f) {
            HomeScreen(state = longText, actions = HomeActions.Noop)
        }

    @Test
    fun homeEmpty() = capture("home_empty", widthDp = 412) {
        HomeScreen(state = HomeUiState(isLoading = false), actions = HomeActions.Noop)
    }

    @Test
    fun homeLoading() = capture("home_loading", widthDp = 412) {
        HomeScreen(state = HomeUiState(isLoading = true), actions = HomeActions.Noop)
    }
}
