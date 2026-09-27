/*
 * LyricsScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.lyrics

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Lyrics (issue #10 phase 4K3): loading, empty, error, the plain reading
 * body (with a stanza break) and the synced body with a mid-list active line. Fully
 * deterministic - fake [LyricsUiState], a fixed [positionMs]. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h900dp-xxhdpi")
class LyricsScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val plainLines = listOf(
        "Is this the real life?",
        "Is this just fantasy?",
        "Caught in a landslide",
        "",
        "No escape from reality",
    )

    private val syncedLines = listOf(
        LyricsLineUi(0L, "Is this the real life?"),
        LyricsLineUi(4_000L, "Is this just fantasy?"),
        LyricsLineUi(8_000L, "Caught in a landslide"),
        LyricsLineUi(12_000L, "No escape from reality"),
        LyricsLineUi(16_000L, "Open your eyes"),
    )

    private fun capture(
        tag: String,
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
                            .width(412.dp)
                            .heightIn(max = 900.dp)
                            .background(TakiTheme.colors.black),
                    ) {
                        content()
                    }
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(
        state: LyricsUiState,
        positionMs: () -> Long = { 0L },
    ): @Composable () -> Unit =
        { LyricsScreen(state = state, positionMs = positionMs, actions = LyricsActions({}, {}, {})) }

    private val identity = LyricsUiState(trackId = "t1", title = "Bohemian Rhapsody", artist = "Queen")

    @Test
    fun lyricsLoading() = capture("lyrics_loading") {
        screen(identity.copy(content = LyricsContent.Loading))()
    }

    @Test
    fun lyricsEmpty() = capture("lyrics_empty") {
        screen(identity.copy(content = LyricsContent.Empty))()
    }

    @Test
    fun lyricsError() = capture("lyrics_error") {
        screen(identity.copy(content = LyricsContent.Error))()
    }

    @Test
    fun lyricsPlain() = capture("lyrics_plain") {
        screen(identity.copy(content = LyricsContent.Plain(plainLines)))()
    }

    @Test
    fun lyricsSyncedStandard() = capture("lyrics_synced_standard") {
        screen(identity.copy(content = LyricsContent.Synced(syncedLines)), positionMs = { 9_000L })()
    }

    @Test
    fun lyricsSyncedFontScale130() = capture("lyrics_synced_font_1_30", fontScale = 1.30f) {
        screen(identity.copy(content = LyricsContent.Synced(syncedLines)), positionMs = { 9_000L })()
    }
}
