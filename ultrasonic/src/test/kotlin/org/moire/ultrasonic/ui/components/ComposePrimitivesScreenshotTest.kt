/*
 * ComposePrimitivesScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Roborazzi screenshot harness, proven on the leaf primitives. JVM only - no emulator,
 * no network, no server or media state. Fake, fixed inputs; deterministic output.
 *
 * Update goldens intentionally with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 * A plain run verifies against `src/test/screenshots/`. See that folder's README.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ComposePrimitivesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Column(
                    modifier = Modifier
                        .testTag(tag)
                        .width(360.dp)
                        .background(TakiTheme.colors.black)
                        .padding(TakiTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md)
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    @Test
    fun takiArtwork() = capture("taki_artwork") {
        TakiArtwork(model = null, contentDescription = "Album art placeholder")
    }

    @Test
    fun takiIconButton() = capture("taki_icon_button") {
        TakiIconButton(
            onClick = {},
            painter = painterResource(R.drawable.media_start),
            contentDescription = "Play"
        )
        TakiIconButton(
            onClick = {},
            painter = painterResource(R.drawable.ic_radio),
            contentDescription = "Shuffle",
            selected = true
        )
    }

    @Test
    fun takiSectionHeader() = capture("taki_section_header") {
        TakiSectionHeader(title = "Recently played", actionLabel = "See all", onActionClick = {})
    }

    @Test
    fun libraryBrowseRow() = capture("library_browse_row") {
        LibraryBrowseRow(
            label = "Albums",
            leadingPainter = painterResource(R.drawable.ic_library),
            onClick = {},
            count = 128,
            showDivider = true
        )
        LibraryBrowseRow(
            label = "Artists",
            leadingPainter = painterResource(R.drawable.ic_artist),
            onClick = {}
        )
    }

    @Test
    fun takiScreenHeader() = capture("taki_screen_header") {
        TakiScreenHeader(onBack = {}, title = "Box Sets")
    }

    @Test
    fun takiFloatingSurface() = capture("taki_floating_surface") {
        TakiFloatingSurface(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            topEdge = true,
        ) {
            Text(
                text = "Floating chrome",
                style = TakiTheme.type.titleSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // The atmospheric wash on the featured card ([TakiAtmosphericSurface]) depends on Coil
    // resolving an image plus a RenderEffect blur - neither is deterministic under Robolectric,
    // so there is no JVM golden for it. Its behaviour is covered by
    // HomeScreenComposeTest#`the featured card still works with the atmospheric wash enabled`
    // and by the Pixel 7 review.
}
