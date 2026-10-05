/*
 * TakiSheetsComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared transient-sheet primitives behind every overlay migrated in issue #10 phase 5A6:
 * what they show, what they report, and - as importantly - what dismissing never does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class TakiSheetsComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tag = "test_sheet"

    private fun pressBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun options(count: Int) = (1..count).map { TakiPickerOption("k$it", "Option $it") }

    // ---- Picker ------------------------------------------------------------------------------

    @Test
    fun `picker shows its title and the options in order`() {
        compose.setContent {
            TakiTheme {
                TakiPickerSheet("Pick one", options(3), {}, {}, "Cancel", 0.dp, tag)
            }
        }
        compose.onNodeWithText("Pick one").assertIsDisplayed()
        compose.onNodeWithText("Pick one").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val tops = (1..3).map {
            compose.onNodeWithTag(takiPickerOptionTestTag("k$it")).getBoundsInRoot().top
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `picker reports the tapped key exactly once`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            TakiTheme { TakiPickerSheet("Pick", options(3), { picked += it }, {}, "Cancel", 0.dp, tag) }
        }
        compose.onNodeWithTag(takiPickerOptionTestTag("k2")).performClick()
        compose.onNodeWithTag(takiPickerOptionTestTag("k2")).performClick()
        compose.onNodeWithTag(takiPickerOptionTestTag("k3")).performClick()
        assertEquals(listOf("k2"), picked)
    }

    @Test
    fun `tapping a non-interactive part of the panel does not dismiss the sheet`() {
        var dismissed = 0
        compose.setContent {
            TakiTheme {
                TakiConfirmSheet("Confirm", "Delete Mix?", "Delete", "Cancel", {}, { dismissed++ }, 0.dp, tag)
            }
        }
        // Found on a Pixel 7: these taps used to fall through to the scrim behind the panel.
        compose.onNodeWithText("Confirm").performTouchInput { click() }
        compose.onNodeWithText("Delete Mix?").performTouchInput { click() }
        compose.onNodeWithTag(tag).performTouchInput { click(topCenter + androidx.compose.ui.geometry.Offset(0f, 4f)) }
        assertEquals("a tap inside the panel must never dismiss it", 0, dismissed)
        // ...while a genuine tap on the scrim still does.
        compose.onNodeWithTag(takiSheetScrimTestTag(tag)).performTouchInput { click(topCenter) }
        assertEquals(1, dismissed)
    }

    @Test
    fun `picker scrim and Back dismiss without selecting`() {
        val picked = mutableListOf<String>()
        var dismissed = 0
        compose.setContent {
            TakiTheme {
                TakiPickerSheet("Pick", options(3), { picked += it }, { dismissed++ }, "Cancel", 0.dp, tag)
            }
        }
        compose.onNodeWithTag(takiSheetScrimTestTag(tag)).performClick()
        assertEquals(1, dismissed)
        pressBack()
        assertEquals(2, dismissed)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `picker rows are at least 48dp tall`() {
        compose.setContent { TakiTheme { TakiPickerSheet("Pick", options(3), {}, {}, "Cancel", 0.dp, tag) } }
        (1..3).forEach {
            val bounds = compose.onNodeWithTag(takiPickerOptionTestTag("k$it")).getBoundsInRoot()
            assertTrue(bounds.height >= 48.dp)
        }
    }

    @Test
    fun `a long picker scrolls inside the sheet instead of overflowing`() {
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    TakiPickerSheet("Pick", options(40), {}, {}, "Cancel", 0.dp, tag)
                }
            }
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
        assertTrue(compose.onNodeWithTag(tag).getBoundsInRoot().height <= 400.dp)
        val offscreen = compose.onAllNodesWithTag(takiPickerOptionTestTag("k40")).fetchSemanticsNodes()
        assertTrue("the last row of a 40-row list must not be composed before scrolling", offscreen.isEmpty())
        compose.onNodeWithTag(TAKI_PICKER_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(takiPickerOptionTestTag("k40")))
        compose.onNodeWithTag(takiPickerOptionTestTag("k40")).assertIsDisplayed()
    }

    @Test
    fun `the bottom inset lifts the last row above the floating chrome`() {
        val inset: Dp = 96.dp
        compose.setContent {
            TakiTheme { TakiPickerSheet("Pick", options(2), {}, {}, "Cancel", inset, tag) }
        }
        val rootHeight = compose.onNodeWithTag(takiSheetScrimTestTag(tag)).getBoundsInRoot().height
        val lastBottom = compose.onNodeWithTag(takiPickerOptionTestTag("k2")).getBoundsInRoot().bottom
        assertTrue("last row must clear the inset", lastBottom <= rootHeight - inset)
    }

    // ---- Confirm -----------------------------------------------------------------------------

    private fun confirmSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        compose.setContent {
            TakiTheme {
                TakiConfirmSheet(
                    title = "Confirm",
                    message = "Delete Mix?",
                    confirmLabel = "Delete",
                    dismissLabel = "Cancel",
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    bottomContentInset = 0.dp,
                    sheetTestTag = tag,
                )
            }
        }
    }

    @Test
    fun `confirm sheet shows title message and explicit action labels`() {
        confirmSheet({}, {})
        compose.onNodeWithText("Confirm").assertIsDisplayed()
        compose.onNodeWithText("Delete Mix?").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `confirm fires exactly once even on a double tap`() {
        var confirmed = 0
        confirmSheet({ confirmed++ }, {})
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `cancel scrim and Back never confirm`() {
        var confirmed = 0
        var dismissed = 0
        confirmSheet({ confirmed++ }, { dismissed++ })
        compose.onNodeWithTag(TAKI_SHEET_DISMISS_ACTION_TEST_TAG).performClick()
        compose.onNodeWithTag(takiSheetScrimTestTag(tag)).performClick()
        pressBack()
        assertEquals(0, confirmed)
        assertEquals(3, dismissed)
    }

    @Test
    fun `action buttons are at least 48dp tall`() {
        confirmSheet({}, {})
        assertTrue(compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).getBoundsInRoot().height >= 48.dp)
        assertTrue(compose.onNodeWithTag(TAKI_SHEET_DISMISS_ACTION_TEST_TAG).getBoundsInRoot().height >= 48.dp)
    }

    // ---- Message -----------------------------------------------------------------------------

    @Test
    fun `message sheet shows its text and OK dismisses`() {
        var dismissed = 0
        compose.setContent {
            TakiTheme {
                TakiMessageSheet(
                    title = "Error",
                    message = AnnotatedString("Server unreachable"),
                    okLabel = "OK",
                    onDismiss = { dismissed++ },
                    bottomContentInset = 0.dp,
                    sheetTestTag = tag,
                )
            }
        }
        compose.onNodeWithText("Server unreachable").assertIsDisplayed()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `back handler is removed when the sheet leaves the composition`() {
        var dismissed = 0
        val show = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            TakiTheme {
                if (show.value) TakiPickerSheet("Pick", options(1), {}, { dismissed++ }, "Cancel", 0.dp, tag)
            }
        }
        show.value = false
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(0, dismissed)
        assertEquals(0, compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
        compose.onAllNodesWithTag(tag).assertCountEquals(0)
    }

    // ---- Linkify -----------------------------------------------------------------------------

    @Test
    fun `linkify turns web urls into tappable links and leaves the text alone`() {
        val text = "See https://example.com/a and example.org today"
        val linked = linkifyWebUrls(text, androidx.compose.ui.graphics.Color.Red)
        assertEquals(text, linked.text)
        val links = linked.getLinkAnnotations(0, text.length)
        assertEquals(2, links.size)
        val urls = links.map { (it.item as LinkAnnotation.Url).url }
        assertTrue(urls.contains("https://example.com/a"))
        assertTrue(urls.contains("http://example.org"))
    }
}
