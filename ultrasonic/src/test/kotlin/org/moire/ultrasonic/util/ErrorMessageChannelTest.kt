/*
 * ErrorMessageChannelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.components.APP_ERROR_SHEET_TEST_TAG
import org.moire.ultrasonic.ui.components.AppErrorSheet
import org.moire.ultrasonic.ui.components.TAKI_SHEET_PRIMARY_ACTION_TEST_TAG
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app-wide error sheet (issue #10 phase 5A6) that replaced `CommunicationError`'s
 * `ErrorDialog`: queueing only while a host is attached, oldest first, one at a time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h900dp-xxhdpi")
class ErrorMessageChannelTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clean() = ErrorMessageChannel.reset()

    @After
    fun cleanUp() = ErrorMessageChannel.reset()

    @Test
    fun `a message with no host attached is dropped, like the legacy dialog without an Activity`() {
        assertFalse(ErrorMessageChannel.post("boom"))
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
    }

    @Test
    fun `messages queue oldest first and dismissing shows the next one`() {
        ErrorMessageChannel.attachHost()
        assertTrue(ErrorMessageChannel.post("first"))
        assertTrue(ErrorMessageChannel.post("second"))
        assertEquals(listOf("first", "second"), ErrorMessageChannel.messages.value)
        ErrorMessageChannel.dismissCurrent()
        assertEquals(listOf("second"), ErrorMessageChannel.messages.value)
        ErrorMessageChannel.dismissCurrent()
        ErrorMessageChannel.dismissCurrent() // extra dismiss on an empty queue is harmless
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
    }

    @Test
    fun `the last host leaving drops whatever was pending`() {
        ErrorMessageChannel.attachHost()
        ErrorMessageChannel.attachHost()
        ErrorMessageChannel.post("pending")
        ErrorMessageChannel.detachHost()
        assertEquals(listOf("pending"), ErrorMessageChannel.messages.value)
        ErrorMessageChannel.detachHost()
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
        assertFalse(ErrorMessageChannel.post("late"))
    }

    @Test
    fun `handleError without a context shows nothing`() {
        ErrorMessageChannel.attachHost()
        CommunicationError.handleError(java.io.FileNotFoundException("gone"), null)
        assertTrue(ErrorMessageChannel.messages.value.isEmpty())
    }

    @Test
    fun `the error sheet shows the Error title and the oldest message, OK dismisses it`() {
        val shown = androidx.compose.runtime.mutableStateOf(listOf("first problem", "second problem"))
        compose.setContent {
            TakiTheme {
                AppErrorSheet(
                    messages = shown.value,
                    onDismiss = { shown.value = shown.value.drop(1) },
                    bottomContentInset = 0.dp,
                )
            }
        }
        compose.onNodeWithText("Error").assertIsDisplayed()
        compose.onNodeWithText("first problem").assertIsDisplayed()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        compose.onNodeWithText("second problem").assertIsDisplayed()
        compose.onNodeWithTag(TAKI_SHEET_PRIMARY_ACTION_TEST_TAG).performClick()
        compose.onNodeWithTag(APP_ERROR_SHEET_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `with nothing pending the error sheet draws nothing`() {
        compose.setContent {
            TakiTheme { AppErrorSheet(emptyList(), {}, 0.dp) }
        }
        compose.onNodeWithTag(APP_ERROR_SHEET_TEST_TAG).assertDoesNotExist()
    }
}
