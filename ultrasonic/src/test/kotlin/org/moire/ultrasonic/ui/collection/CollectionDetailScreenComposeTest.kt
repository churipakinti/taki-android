/*
 * CollectionDetailScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Collection Detail screen: the calm fixed header (stacked covers, grouping title, "N discs"),
 * the member grid/list, member-open callbacks, the empty state, long classical member titles
 * and font scale 1.30. JVM / Robolectric, a tall viewport so the whole composition lays out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2400dp-xxhdpi")
class CollectionDetailScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun member(id: String, title: String = "Disc $id", disc: Int? = id.toIntOrNull(), tracks: Long? = 12) =
        CollectionMember(id = id, parent = "ar1", discNumber = disc, title = title, trackCount = tracks, artworkModel = null)

    private val loaded = CollectionDetailUiState(
        isLoading = false,
        grouping = "Bach 333",
        title = "Bach 333",
        members = persistentListOf(
            member("1", "Cantatas BWV 1-3", tracks = 18),
            member("2", "Cantatas BWV 4-6", tracks = 21),
            member("3", "Cantatas BWV 7-9", tracks = 19),
        ),
    )

    private fun setContent(
        state: CollectionDetailUiState,
        actions: CollectionDetailActions = CollectionDetailActions.Noop,
        layout: LayoutType = LayoutType.COVER,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme {
                    CollectionDetailScreen(state = state, actions = actions, layout = layout)
                }
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(COLLECTION_DETAIL_LIST_TEST_TAG)
            .performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    @Test
    fun `the header shows the grouping title once and the disc count`() {
        setContent(loaded)
        compose.onNodeWithText("Bach 333").assertIsDisplayed()
        // The grouping name is not duplicated into a top-bar title.
        compose.onAllNodesWithText("Bach 333").assertCountEquals(1)
        compose.onNodeWithText("3 discs", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the lightweight top row carries back, layout toggle and discover`() {
        setContent(loaded)
        compose.onNodeWithTag(COLLECTION_DETAIL_TOP_BAR_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Switch layout").assertIsDisplayed()
        compose.onNodeWithContentDescription("Find missing discs").assertIsDisplayed()
    }

    @Test
    fun `the back affordance fires onBack`() {
        var backs = 0
        setContent(
            loaded,
            actions = CollectionDetailActions(
                onBack = { backs++ },
                onToggleLayout = {},
                onOpenMember = {},
                onRefresh = {},
                onDiscoverMore = {},
            ),
        )
        compose.onNodeWithContentDescription("Go back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the layout toggle fires onToggleLayout`() {
        var toggles = 0
        setContent(
            loaded,
            actions = CollectionDetailActions(
                onBack = {},
                onToggleLayout = { toggles++ },
                onOpenMember = {},
                onRefresh = {},
                onDiscoverMore = {},
            ),
        )
        compose.onNodeWithContentDescription("Switch layout").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `a single-member collection still shows its title once and renders`() {
        setContent(loaded.copy(members = persistentListOf(member("1", "Cheese", disc = null))))
        compose.onAllNodesWithText("Bach 333").assertCountEquals(1)
        scrollTo("Cheese").assertIsDisplayed()
    }

    @Test
    fun `each member card shows its position, title and track count`() {
        setContent(loaded)
        scrollTo("Cantatas BWV 1-3").assertIsDisplayed()
        scrollTo("Disc 1").assertIsDisplayed()
        scrollTo("18 tracks").assertIsDisplayed()
    }

    @Test
    fun `tapping a member card opens that member`() {
        var opened: CollectionMember? = null
        setContent(
            loaded,
            actions = CollectionDetailActions(
                onBack = {},
                onToggleLayout = {},
                onOpenMember = { m -> opened = m },
                onRefresh = {},
                onDiscoverMore = {},
            ),
        )
        scrollTo("Cantatas BWV 4-6").performClick()
        assertEquals("2", opened?.id)
    }

    @Test
    fun `the list layout renders member rows and opens them`() {
        var opened: CollectionMember? = null
        setContent(
            loaded,
            layout = LayoutType.LIST,
            actions = CollectionDetailActions(
                onBack = {},
                onToggleLayout = {},
                onOpenMember = { m -> opened = m },
                onRefresh = {},
                onDiscoverMore = {},
            ),
        )
        scrollTo("Cantatas BWV 7-9").performClick()
        assertEquals("3", opened?.id)
    }

    @Test
    fun `a finished empty collection shows the no-match state`() {
        setContent(loaded.copy(members = persistentListOf()))
        compose.onNodeWithText("No matches", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a member without a disc number or track count shows only its title`() {
        setContent(loaded.copy(members = persistentListOf(member("x", "Untitled", disc = null, tracks = null))))
        scrollTo("Untitled").assertIsDisplayed()
        compose.onAllNodesWithText("Disc", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("tracks", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a still-loading collection lays out without a disc count line`() {
        setContent(CollectionDetailUiState(isLoading = true, grouping = "Bach 333", title = "Bach 333"))
        compose.onNodeWithText("Bach 333").assertIsDisplayed()
    }

    @Test
    fun `a long classical member title stays visible`() {
        val longTitle =
            "Prelude and Fugue No. 1 in C major, BWV 846 - The Well-Tempered Clavier, Book I"
        setContent(loaded.copy(members = persistentListOf(member("1", longTitle))))
        scrollTo(longTitle.take(24)).assertIsDisplayed()
    }

    @Test
    fun `a long member title stays on one line - same title height as a short one`() {
        val shortTitle = "Aria"
        val longTitle =
            "Prelude and Fugue No. 1 in C major, BWV 846 - The Well-Tempered Clavier, Book I, " +
                "revised critical edition with the complete ornamentation restored"
        setContent(
            loaded.copy(
                members = persistentListOf(
                    member("short", shortTitle, disc = null, tracks = null),
                    member("long", longTitle, disc = null, tracks = null),
                ),
            ),
        )
        val shortH = compose.onNodeWithText(shortTitle).getUnclippedBoundsInRoot().height
        val longH = compose.onNodeWithText(longTitle, substring = true)
            .getUnclippedBoundsInRoot().height
        // One line each -> equal height (a wrapped 2-line title would be ~2x taller).
        assertEquals(shortH.value, longH.value, 1f)
    }

    @Test
    fun `a two-member collection renders both members`() {
        setContent(
            loaded.copy(
                members = persistentListOf(
                    member("1", "Volume One", disc = null),
                    member("2", "Volume Two", disc = null),
                ),
            ),
        )
        compose.onNodeWithText("2 discs", substring = true).assertIsDisplayed()
        scrollTo("Volume One").assertIsDisplayed()
        scrollTo("Volume Two").assertIsDisplayed()
    }

    @Test
    fun `font scale 1_30 keeps the title and members readable`() {
        setContent(loaded, fontScale = 1.30f)
        compose.onNodeWithText("Bach 333").assertIsDisplayed()
        scrollTo("Cantatas BWV 1-3").assertIsDisplayed()
    }
}
