/*
 * LyricsScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.lyrics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lyrics screen (issue #10 phase 4K3): the loading/empty/error states, the plain and synced
 * bodies, and the seek/retry/back wiring. Deterministic - [positionMs] is a fixed lambda rather
 * than a live playback clock, so timing-driven auto-follow is covered by the pure
 * [activeLineIndex] unit tests instead of here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h892dp-xxhdpi")
class LyricsScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: LyricsUiState,
        positionMs: () -> Long = { 0L },
        actions: LyricsActions = noopActions(),
    ) {
        compose.setContent {
            TakiTheme { LyricsScreen(state = state, positionMs = positionMs, actions = actions) }
        }
    }

    private fun noopActions(
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onSeek: (Long) -> Unit = {},
    ) = LyricsActions(onBack, onRetry, onSeek)

    @Test
    fun `the header shows the Lyrics title and its back affordance fires onBack`() {
        var backs = 0
        setContent(LyricsUiState(), actions = noopActions(onBack = { backs++ }))

        compose.onNodeWithText("Lyrics").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the track title and artist show above the lyrics body`() {
        setContent(LyricsUiState(title = "Bohemian Rhapsody", artist = "Queen"))

        compose.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        compose.onNodeWithText("Queen").assertIsDisplayed()
    }

    @Test
    fun `loading shows a labelled indicator`() {
        setContent(LyricsUiState(content = LyricsContent.Loading))
        compose.onNodeWithContentDescription("Loading lyrics").assertIsDisplayed()
    }

    @Test
    fun `no lyrics from the server shows the empty state`() {
        setContent(LyricsUiState(content = LyricsContent.Empty))
        compose.onNodeWithText("No lyrics found").assertIsDisplayed()
    }

    @Test
    fun `a failed load shows the error state and its retry action fires onRetry`() {
        var retries = 0
        setContent(LyricsUiState(content = LyricsContent.Error), actions = noopActions(onRetry = { retries++ }))

        compose.onNodeWithText("Couldn't load lyrics").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `plain lyrics render every source line, in order`() {
        val lines = listOf("First line", "Second line", "Third line")
        setContent(LyricsUiState(content = LyricsContent.Plain(lines)))

        compose.onNodeWithTag(LYRICS_LIST_TEST_TAG).assertIsDisplayed()
        lines.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `synced lyrics render every line`() {
        val lines = listOf(LyricsLineUi(0L, "First line"), LyricsLineUi(1000L, "Second line"))
        setContent(LyricsUiState(content = LyricsContent.Synced(lines)))

        compose.onNodeWithText("First line").assertIsDisplayed()
        compose.onNodeWithText("Second line").assertIsDisplayed()
    }

    @Test
    fun `the line active for the given position is marked from first composition`() {
        val lines = listOf(LyricsLineUi(0L, "First line"), LyricsLineUi(1000L, "Second line"))
        setContent(LyricsUiState(content = LyricsContent.Synced(lines)), positionMs = { 1200L })

        compose.onNodeWithTag(LYRICS_ACTIVE_LINE_TEST_TAG).assertTextEquals("Second line")
    }

    @Test
    fun `tapping a synced line seeks to its timestamp`() {
        val lines = listOf(LyricsLineUi(0L, "First line"), LyricsLineUi(4200L, "Second line"))
        var sought: Long? = null
        setContent(
            LyricsUiState(content = LyricsContent.Synced(lines)),
            actions = noopActions(onSeek = { sought = it }),
        )

        compose.onNodeWithText("Second line").performClick()
        assertEquals(4200L, sought)
    }

    @Test
    fun `the resume-to-current-line chip is hidden until following is suspended`() {
        val lines = listOf(LyricsLineUi(0L, "First line"), LyricsLineUi(1000L, "Second line"))
        setContent(LyricsUiState(content = LyricsContent.Synced(lines)))

        compose.onNodeWithTag(LYRICS_RESUME_TEST_TAG).assertDoesNotExist()
    }
}
