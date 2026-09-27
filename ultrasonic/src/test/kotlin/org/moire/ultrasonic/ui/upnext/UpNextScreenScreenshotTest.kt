/*
 * UpNextScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.upnext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Roborazzi goldens for Up Next (issue #10 phase 4K1): a populated queue and the "nothing up
 * next" state. Deterministic - fake state, null artwork (the neutral placeholder), fixed progress.
 * Record with `./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h900dp-xxhdpi")
class UpNextScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val current = UpNextCurrentUi("Paranoid Android", "Radiohead", null, true)

    private val upcoming = listOf(
        "Karma Police" to "Radiohead",
        "No Surprises" to "Radiohead",
        "Everything In Its Right Place" to "Radiohead",
        "Lucky" to "Radiohead",
        "Exit Music (For a Film)" to "Radiohead",
        "Let Down" to "Radiohead",
        "Airbag" to "Radiohead",
    ).mapIndexed { i, (title, artist) ->
        UpNextTrackUi("k$i", title, artist, 4 + i, emptyList())
    }

    private fun capture(tag: String, state: UpNextUiState) {
        compose.setContent {
            TakiTheme {
                Box(
                    Modifier
                        .testTag(tag)
                        .width(412.dp)
                        .heightIn(max = 900.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    UpNextScreen(
                        state = state,
                        progressFraction = { 0.42f },
                        artworkFor = { null },
                        actions = UpNextActions.Noop,
                    )
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Test
    fun upNextPopulated() = capture("up_next_populated", UpNextUiState(current, upcoming))

    @Test
    fun upNextEmpty() = capture("up_next_empty", UpNextUiState(current, emptyList()))
}
