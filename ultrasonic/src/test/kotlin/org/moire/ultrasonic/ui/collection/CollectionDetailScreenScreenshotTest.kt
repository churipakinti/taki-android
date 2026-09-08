/*
 * CollectionDetailScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

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
import org.moire.ultrasonic.util.LayoutType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Collection Detail. Fully deterministic - fake
 * [CollectionDetailUiState], no artwork network. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 * See ultrasonic/src/test/screenshots/README.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h2400dp-xxhdpi")
class CollectionDetailScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun member(id: String, title: String, disc: Int? = id.toIntOrNull(), tracks: Long? = 14) =
        CollectionMember(id = id, parent = "ar1", discNumber = disc, title = title, trackCount = tracks, artworkModel = null)

    private val bach = CollectionDetailUiState(
        isLoading = false,
        grouping = "Bach 333",
        title = "Bach 333",
        members = persistentListOf(
            member("1", "Cantatas BWV 1-3"),
            member("2", "Cantatas BWV 4-6"),
            member("3", "Cantatas BWV 7-9"),
            member("4", "Cantatas BWV 10-12"),
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

    private fun screen(
        state: CollectionDetailUiState,
        layout: LayoutType = LayoutType.COVER,
    ): @Composable () -> Unit =
        { CollectionDetailScreen(state = state, actions = CollectionDetailActions.Noop, layout = layout) }

    @Test
    fun collectionStandard() = capture("collection_standard", widthDp = 412) {
        screen(bach)()
    }

    @Test
    fun collectionManyMembers() = capture("collection_many_members", widthDp = 412) {
        screen(
            bach.copy(
                members = persistentListOf(
                    member("1", "Orchestral Suites Nos. 1 & 2"),
                    member("2", "Orchestral Suites Nos. 3 & 4"),
                    member("3", "Brandenburg Concertos Nos. 1-3"),
                    member("4", "Brandenburg Concertos Nos. 4-6"),
                    member("5", "Violin Concertos BWV 1041-1043"),
                    member("6", "Harpsichord Concertos BWV 1052-1054"),
                    member("7", "The Well-Tempered Clavier, Book I"),
                    member("8", "The Well-Tempered Clavier, Book II"),
                ),
            ),
        )()
    }

    @Test
    fun collectionTwoMembers() = capture("collection_two_members", widthDp = 412) {
        screen(
            bach.copy(
                title = "Mercury Living Presence",
                members = persistentListOf(
                    member("1", "Volume 1 - The Collector's Edition"),
                    member("2", "Volume 2 - The Collector's Edition"),
                ),
            ),
        )()
    }

    @Test
    fun collectionList() = capture("collection_list", widthDp = 412) {
        screen(bach, layout = LayoutType.LIST)()
    }

    @Test
    fun collectionClassicalLongNames() = capture("collection_bach_classical", widthDp = 412) {
        screen(
            bach.copy(
                title = "J. S. Bach - Complete Works (333rd Anniversary Edition)",
                members = persistentListOf(
                    member("6", "Prelude and Fugue No. 1 in C major, BWV 846: The Well-Tempered Clavier"),
                    member("7", "Toccata and Fugue in D minor, BWV 565 - Great Organ Works"),
                    member("108", "St Matthew Passion, BWV 244: Part One", tracks = 34),
                ),
            ),
        )()
    }

    @Test
    fun collectionCompact360() = capture("collection_compact_360", widthDp = 360) {
        screen(bach)()
    }

    @Test
    fun collectionFontScale130() = capture("collection_font_1_30", widthDp = 412, fontScale = 1.30f) {
        screen(bach)()
    }

    @Test
    fun collectionEmpty() = capture("collection_empty", widthDp = 412) {
        screen(bach.copy(members = persistentListOf()))()
    }
}
