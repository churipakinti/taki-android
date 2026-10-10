/*
 * EditServerDiscardBackTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.model.EditServerViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #28 regression: Back while the "discard changes" sheet is open must close ONLY the sheet. The
 * host (`EditServerFragment`) registers its own Back callback -> `requestBack()` first, so before
 * the fix Back reached it, which re-raised the sheet and the user could never cancel with Back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class EditServerDiscardBackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var model: EditServerViewModel
    private val navigation = mutableListOf<EditServerNavigationEvent>()

    @Before
    fun setUp() {
        RobolectricUAppContext.install()
        model = EditServerViewModel(ApplicationProvider.getApplicationContext<Application>())
        model.onNavigate = { navigation += it }
        model.load(EditServerMode.New)
    }

    private fun pressBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun show() {
        // Registered BEFORE the composition, exactly like the fragment's backCallback.
        compose.activity.onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = model.requestBack()
            },
        )
        compose.setContent {
            TakiTheme {
                val state by model.uiState.collectAsState()
                EditServerScreen(
                    state = state,
                    actions = EditServerActions.Noop.copy(
                        onBack = model::requestBack,
                        onDiscardCancel = model::cancelDiscard,
                        onDiscardConfirm = model::confirmDiscard,
                    ),
                    bottomContentInset = 0.dp,
                )
            }
        }
    }

    @Test
    fun `dirty form - discard sheet - Back closes only the sheet and the form stays dirty`() {
        show()
        model.onUsernameChange("joseph")
        pressBack() // the host's Back -> requestBack -> sheet opens
        assertTrue(model.uiState.value.pendingDiscard)
        compose.onNodeWithTag(DISCARD_SERVER_SHEET_TEST_TAG).assertExists()

        pressBack() // the sheet owns this one
        assertFalse("sheet must close and stay closed", model.uiState.value.pendingDiscard)
        assertTrue("must not leave Edit Server", navigation.isEmpty())
        assertEquals("form data intact", "joseph", model.uiState.value.username)

        // Sheet hidden again: Back is the host's, as before (re-asks, does not leave).
        pressBack()
        assertTrue(model.uiState.value.pendingDiscard)
        assertTrue(navigation.isEmpty())
    }

    @Test
    fun `panel tap keeps the sheet open, scrim tap cancels, Discard still leaves`() {
        show()
        model.onUsernameChange("joseph")
        pressBack()
        compose.onNodeWithTag(DISCARD_SERVER_SHEET_TEST_TAG).performTouchInput { click(topCenter + androidx.compose.ui.geometry.Offset(0f, 6f)) }
        compose.waitForIdle()
        assertTrue(model.uiState.value.pendingDiscard)

        compose.onNodeWithTag(DISCARD_SERVER_SCRIM_TEST_TAG).performTouchInput { click(topCenter + androidx.compose.ui.geometry.Offset(0f, 40f)) }
        compose.waitForIdle()
        assertFalse(model.uiState.value.pendingDiscard)
        assertTrue(navigation.isEmpty())

        pressBack()
        compose.onNodeWithTag(DISCARD_SERVER_CONFIRM_TEST_TAG).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(EditServerNavigationEvent.NavigateUp), navigation)
    }
}
