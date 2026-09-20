/*
 * AlbumDetailModesScreenshotTest.kt
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for the two Album Detail modes that look different from the online id3
 * album (issue #10 phase 4H1): the downloaded album (per-track status indicators, no heart or
 * Download in offline mode) and a folder album with sub-folder rows. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1500dp-xxhdpi")
class AlbumDetailModesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun tk(id: String, title: String) =
        AlbumDetailRow.Track(id, "0$id.", title, null, "3:00", false)

    private val downloaded = AlbumDetailUiState(
        isLoading = false,
        title = "Cheese",
        artist = "Stromae",
        artistId = "ar1",
        year = "2010",
        songCount = 4,
        totalDuration = "41:50",
        online = false,
        starVisible = false,
        showDownloadStatus = true,
        trackStatuses = persistentMapOf(
            "1" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.DOWNLOADED),
            "2" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.DOWNLOADED),
            "3" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.DOWNLOADING, 60),
            "4" to TrackDownloadIndicator(TrackDownloadIndicator.Kind.FAILED),
        ),
        rows = persistentListOf(
            tk("1", "Bienvenue chez moi"),
            tk("2", "Te quiero"),
            tk("3", "Alors on danse"),
            tk("4", "Rail de musique"),
        ),
    )

    private val folderAlbum = AlbumDetailUiState(
        isLoading = false,
        title = "Complete Live Recordings",
        artist = "Various Artists",
        songCount = 2,
        starVisible = true,
        rows = persistentListOf(
            AlbumDetailRow.Folder("f1", "CD 1 - Opening Night", "The Band"),
            AlbumDetailRow.Folder("f2", "CD 2 - Encore", null),
            tk("1", "Bonus Track"),
            tk("2", "Hidden Track"),
        ),
    )

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .heightIn(max = 1500.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: AlbumDetailUiState): @Composable () -> Unit = {
        AlbumDetailScreen(
            state = state,
            actions = AlbumDetailActions.Noop,
            currentTrackId = null,
            bottomContentInset = 0.dp,
        )
    }

    @Test
    fun albumDetailDownloaded() = capture("album_detail_downloaded") { screen(downloaded)() }

    @Test
    fun albumDetailFolder() = capture("album_detail_folder") { screen(folderAlbum)() }
}
