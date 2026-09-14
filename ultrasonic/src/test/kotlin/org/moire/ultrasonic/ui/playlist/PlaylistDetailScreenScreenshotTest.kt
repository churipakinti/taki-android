/*
 * PlaylistDetailScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Roborazzi goldens for Playlist Detail (issue #10 phase 4F3): the populated hero + track list
 * (Play/Shuffle/header-menu, every row's artist always shown) and the empty state. No separate
 * "playlist-specific actions" golden - the populated state already shows the header menu's
 * distinct "More options" icon (Album Detail's own Download/Info/Star row replaced by this
 * screen's single trailing action), so a third near-identical screenshot would be redundant.
 * Fully deterministic - fake [PlaylistDetailUiState], no artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h2400dp-xxhdpi")
class PlaylistDetailScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun tk(id: String, title: String, artist: String?, dur: String? = "3:00") =
        PlaylistDetailRow(id, null, title, artist, dur, false)

    private val standard = PlaylistDetailUiState(
        isLoading = false,
        title = "Road Trip",
        artist = "Various Artists",
        songCount = 4,
        totalDuration = "15:12",
        rows = persistentListOf(
            tk("1", "So What", "Miles Davis"),
            tk("2", "Take Five", "Dave Brubeck"),
            tk("3", "Man Or Animal", "Audioslave"),
            tk("4", "Mujer del-fin", "Kraken"),
        ),
    )

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .heightIn(max = 2400.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: PlaylistDetailUiState, currentTrackId: String? = null): @Composable () -> Unit =
        { PlaylistDetailScreen(state = state, actions = PlaylistDetailActions.Noop, currentTrackId = currentTrackId) }

    @Test
    fun playlistStandard() = capture("playlist_standard") { screen(standard, currentTrackId = "2")() }

    @Test
    fun playlistEmpty() = capture("playlist_empty_state") {
        screen(standard.copy(rows = persistentListOf(), songCount = 0, totalDuration = null, artist = ""))()
    }
}
