/*
 * ShareCardSheetComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.share

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #20: the share preview sheet is pure presentation over [ShareCardUiState] - loading,
 * ready, error and hidden - and only reports taps through [ShareCardActions].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h900dp-xxhdpi")
class ShareCardSheetComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val request = ShareCardRequest("t", ShareCardContent("Kashmir", "Led Zeppelin", "Physical Graffiti"), null)
    private val preview = Bitmap.createBitmap(108, 192, Bitmap.Config.ARGB_8888)

    private fun string(resId: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId, *args)

    private fun setContent(state: ShareCardUiState, actions: ShareCardActions = ShareCardActions.Noop) {
        compose.setContent {
            TakiTheme {
                Box(Modifier.fillMaxSize()) { ShareCardSheet(state = state, actions = actions) }
            }
        }
    }

    @Test
    fun `hidden shows nothing`() {
        setContent(ShareCardUiState.Hidden)
        compose.onNodeWithTag(SHARE_SHEET_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `generating shows a loading card and keeps Share disabled`() {
        setContent(ShareCardUiState.Generating(request))
        compose.onNodeWithTag(SHARE_PREVIEW_LOADING_TEST_TAG).assertExists()
        compose.onNodeWithTag(SHARE_PREVIEW_TEST_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SHARE_ACTION_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `ready shows the preview with a description and an enabled Share action`() {
        var shared = 0
        setContent(
            ShareCardUiState.Ready(request, preview, File("x.png")),
            ShareCardActions.Noop.copy(onShare = { shared++ }),
        )
        val node = compose.onNodeWithTag(SHARE_PREVIEW_TEST_TAG)
        node.assertExists()
        val description = node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        assertEquals(string(R.string.share_card_preview_description, "Kashmir - Led Zeppelin"), description)
        compose.onNodeWithTag(SHARE_ACTION_TEST_TAG).assertIsEnabled().performClick()
        assertEquals(1, shared)
    }

    @Test
    fun `the preview keeps the 9 by 16 proportions`() {
        setContent(ShareCardUiState.Ready(request, preview, File("x.png")))
        val bounds = compose.onNodeWithTag(SHARE_PREVIEW_TEST_TAG).fetchSemanticsNode().size
        assertEquals(9f / 16f, bounds.width.toFloat() / bounds.height, 0.01f)
    }

    @Test
    fun `error shows a message and retry, and no Share action`() {
        var retried = 0
        setContent(ShareCardUiState.Error(request), ShareCardActions.Noop.copy(onRetry = { retried++ }))
        compose.onNodeWithTag(SHARE_ERROR_TEST_TAG).assertExists()
        compose.onNodeWithTag(SHARE_ACTION_TEST_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SHARE_RETRY_TEST_TAG).performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `scrim tap dismisses without sharing`() {
        var dismissed = 0
        var shared = 0
        setContent(
            ShareCardUiState.Ready(request, preview, File("x.png")),
            ShareCardActions(onShare = { shared++ }, onRetry = {}, onDismiss = { dismissed++ }),
        )
        // Top-left corner: the scrim's centre is covered by the panel, which swallows taps.
        compose.onNodeWithTag(SHARE_SHEET_SCRIM_TEST_TAG).performTouchInput { click(Offset(8f, 8f)) }
        assertEquals(1, dismissed)
        assertEquals(0, shared)
    }

    @Test
    fun `system back dismisses the open sheet`() {
        var dismissed = 0
        setContent(
            ShareCardUiState.Ready(request, preview, File("x.png")),
            ShareCardActions.Noop.copy(onDismiss = { dismissed++ }),
        )
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue(dismissed >= 1)
    }
}
