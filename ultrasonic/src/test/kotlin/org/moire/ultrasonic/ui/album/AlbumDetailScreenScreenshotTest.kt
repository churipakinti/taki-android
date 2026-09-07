/*
 * AlbumDetailScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

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
 * Roborazzi goldens for Album Detail. Fully deterministic - fake [AlbumDetailUiState], no
 * artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 * See ultrasonic/src/test/screenshots/README.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h2400dp-xxhdpi")
class AlbumDetailScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun tk(id: String, title: String, artist: String? = null, dur: String? = "3:00") =
        AlbumDetailRow.Track(id, null, title, artist, dur, false)

    private val standard = AlbumDetailUiState(
        isLoading = false,
        title = "Kind of Blue",
        artist = "Miles Davis",
        artistId = "ar1",
        year = "1959",
        genre = "Jazz",
        songCount = 5,
        totalDuration = "45:44",
        starVisible = true,
        rows = persistentListOf(
            tk("1", "So What"),
            tk("2", "Freddie Freeloader"),
            tk("3", "Blue in Green"),
            tk("4", "All Blues"),
            tk("5", "Flamenco Sketches"),
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

    private fun screen(state: AlbumDetailUiState, currentTrackId: String? = null): @Composable () -> Unit =
        { AlbumDetailScreen(state = state, actions = AlbumDetailActions.Noop, currentTrackId = currentTrackId) }

    @Test
    fun albumStandard() = capture("album_standard", widthDp = 412) {
        screen(standard, currentTrackId = "2")()
    }

    @Test
    fun albumLongClassical() = capture("album_long_classical", widthDp = 412) {
        screen(
            standard.copy(
                title = "The Well-Tempered Clavier, Book I, BWV 846-869 - Prelude and Fugue No. 1",
                artist = "Johann Sebastian Bach",
                genre = "Classical",
                totalDuration = "1:52:39",
                songCount = 4,
                notes = "Recorded in 1963.",
                rows = persistentListOf(
                    tk("1", "Prelude and Fugue No. 1 in C major, BWV 846: I. Praeludium"),
                    tk("2", "Prelude and Fugue No. 1 in C major, BWV 846: II. Fuga a 4 voci"),
                    tk("3", "Prelude and Fugue No. 2 in C minor, BWV 847: I. Praeludium"),
                    tk("4", "Prelude and Fugue No. 2 in C minor, BWV 847: II. Fuga a 3 voci"),
                ),
            ),
        )()
    }

    @Test
    fun albumMultiDisc() = capture("album_multi_disc", widthDp = 412) {
        screen(
            standard.copy(
                title = "The Köln Concert",
                hasMultipleDiscs = true,
                songCount = 4,
                rows = persistentListOf(
                    AlbumDetailRow.Disc(1),
                    tk("1", "Part I"),
                    tk("2", "Part II a"),
                    AlbumDetailRow.Disc(2),
                    tk("3", "Part II b"),
                    tk("4", "Part II c"),
                ),
            ),
        )()
    }

    @Test
    fun albumLiked() = capture("album_liked", widthDp = 412) {
        screen(standard.copy(isStarred = true, notes = "A landmark 1959 session."))()
    }

    @Test
    fun albumNoArtwork() = capture("album_no_artwork", widthDp = 412) {
        screen(
            standard.copy(
                artworkModel = null,
                artist = "Various Artists",
                artistId = null,
                genre = null,
                year = null,
            ),
        )()
    }

    @Test
    fun albumCompact360() = capture("album_compact_360", widthDp = 360) {
        screen(standard)()
    }

    @Test
    fun albumFontScale130() = capture("album_font_1_30", widthDp = 412, fontScale = 1.30f) {
        screen(standard)()
    }
}
