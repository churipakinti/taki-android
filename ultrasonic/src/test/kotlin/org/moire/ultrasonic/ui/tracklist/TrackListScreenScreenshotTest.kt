/*
 * TrackListScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.tracklist

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
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the shared Track List screen (issue #10 phase 4F1): the "Songs"
 * controls row (populated), the dedicated Liked Songs list (hearts, no controls), Liked Songs
 * empty, and the empty state. Fully deterministic - fake [TrackListUiState], no artwork network.
 * Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class TrackListScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, title: String, subtitle: String, liked: Boolean = false) =
        TrackListRow(id = id, title = title, subtitle = subtitle, artworkModel = null, liked = liked)

    private val songs = TrackListUiState(
        isLoading = false,
        showControls = true,
        sortOrder = SortOrder.ALL_SONGS,
        availableSortOrders = persistentListOf(
            SortOrder.ALL_SONGS,
            SortOrder.RANDOM,
            SortOrder.BY_ARTIST,
            SortOrder.BY_GENRE,
            SortOrder.STARRED,
        ),
        rows = persistentListOf(
            row("t1", "OK Computer", "Radiohead · OK Computer"),
            row("t2", "Kid A", "Radiohead · Kid A"),
            row("t3", "Rumours", "Fleetwood Mac · Rumours"),
            row("t4", "Abbey Road", "The Beatles · Abbey Road"),
        ),
    )

    private val likedSongs = TrackListUiState(
        isLoading = false,
        showControls = false,
        showHeart = true,
        sortOrder = SortOrder.STARRED,
        rows = persistentListOf(
            row("t1", "Combativo", "A.N.I.M.A.L. · Combativo", liked = true),
            row("t2", "Revolution 909", "Daft Punk · Homework", liked = true),
        ),
    )

    private fun capture(
        tag: String,
        widthDp: Int = 420,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(widthDp.dp)
                        .heightIn(max = 1200.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: TrackListUiState): @Composable () -> Unit =
        {
            TrackListScreen(
                state = state,
                actions = TrackListActions.Noop,
                bottomContentInset = 0.dp,
            )
        }

    @Test
    fun trackListSongs() = capture("track_list_songs") { screen(songs)() }

    @Test
    fun trackListLikedSongs() = capture("track_list_liked_songs") { screen(likedSongs)() }

    @Test
    fun trackListLikedSongsEmpty() = capture("track_list_liked_songs_empty") {
        screen(likedSongs.copy(rows = persistentListOf()))()
    }

    @Test
    fun trackListEmpty() = capture("track_list_empty") {
        screen(songs.copy(rows = persistentListOf(), availableSortOrders = persistentListOf()))()
    }
}
