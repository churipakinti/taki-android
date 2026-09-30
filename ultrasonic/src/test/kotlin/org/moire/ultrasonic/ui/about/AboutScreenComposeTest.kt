/*
 * AboutScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.about

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner

/**
 * About (post-issue-#10 residual migration, phase 5A1): app identity, version, tagline, blurb,
 * and one visible action - "Report a problem". Mirrors the legacy `AboutFragment`/`help.xml`
 * exactly, including the permanently-hidden "Visit website" button (never shown in production).
 */
@RunWith(RobolectricTestRunner::class)
class AboutScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(actions: AboutActions = AboutActions.Noop) {
        compose.setContent {
            TakiTheme {
                AboutScreen(versionName = "4.0.0", actions = actions)
            }
        }
    }

    @Test
    fun `the app name, version and tagline are shown`() {
        setContent()
        compose.onNodeWithText("Taki", substring = false).assertIsDisplayed()
        compose.onNodeWithText("4.0.0", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Your music, without distractions.").assertIsDisplayed()
    }

    @Test
    fun `the free-text blurb is shown with HTML tags stripped`() {
        setContent()
        compose.onNodeWithText("Taki is a focused player", substring = true).assertIsDisplayed()
        compose.onNodeWithText("<b>", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the website action is not rendered - it was permanently hidden in the legacy screen too`() {
        setContent()
        compose.onNodeWithText("Visit website").assertDoesNotExist()
    }

    @Test
    fun `tapping Report a problem fires onReportBug`() {
        var tapped = false
        setContent(AboutActions.Noop.copy(onReportBug = { tapped = true }))
        compose.onNodeWithText("Report a problem").assertIsDisplayed().performClick()
        assertTrue(tapped)
    }

    @Test
    fun `the report-bug row is a real touch target at least 48dp tall`() {
        setContent()
        compose.onNodeWithText("Report a problem")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `tapping back fires onBack`() {
        var tapped = false
        setContent(AboutActions.Noop.copy(onBack = { tapped = true }))
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `the header title reads About, not the app name`() {
        setContent()
        compose.onNodeWithText("About").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("About").fetchSemanticsNodes().size)
    }
}
