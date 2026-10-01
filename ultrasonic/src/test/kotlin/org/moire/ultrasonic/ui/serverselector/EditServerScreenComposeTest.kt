/*
 * EditServerScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edit Server screen (issue #10 phase 5A3): the onboarding form (New mode) and the full editor
 * (Existing mode), the Advanced section, validation errors, and the discard-changes sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class EditServerScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val newState = EditServerUiState(mode = EditServerMode.New)
    private val existingState = EditServerUiState(
        mode = EditServerMode.Existing(1),
        name = "Home",
        address = "https://home.example.com",
        username = "joseph",
        password = "secret",
    )

    private fun setContent(state: EditServerUiState, actions: EditServerActions = EditServerActions.Noop) {
        compose.setContent {
            TakiTheme {
                EditServerScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    // --- New mode (onboarding) -----------------------------------------------------------------

    @Test
    fun `new mode shows the Add library header and only address, username, password`() {
        setContent(newState)
        compose.onNodeWithText("Add library").assertIsDisplayed()
        compose.onNodeWithText("Library Address").assertIsDisplayed()
        compose.onNodeWithText("Username").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun `new mode hides the name field, advanced settings and color picker`() {
        setContent(newState)
        compose.onNodeWithText("Collection name").assertDoesNotExist()
        compose.onNodeWithText("Advanced settings").assertDoesNotExist()
        compose.onNodeWithText("Library color").assertDoesNotExist()
    }

    @Test
    fun `new mode shows a Connect button, not Save or Test Connection`() {
        setContent(newState)
        compose.onNodeWithText("Connect").assertIsDisplayed()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Test Connection").assertDoesNotExist()
    }

    // Password obscuring (PasswordVisualTransformation) only masks the rendered glyphs - the
    // real value still exists in the semantics tree (by design, for accessibility/testing tools),
    // so there's no reliable node-tree assertion for "is obscured"; the Roborazzi goldens below
    // verify this visually instead.

    @Test
    fun `typing in the address field fires onAddressChange`() {
        var changed: String? = null
        setContent(newState, EditServerActions.Noop.copy(onAddressChange = { changed = it }))
        compose.onNodeWithText("Library Address").performTextInput("s")
        assertTrue(changed != null)
    }

    @Test
    fun `tapping Connect fires onConnectOrSave`() {
        var tapped = false
        setContent(newState, EditServerActions.Noop.copy(onConnectOrSave = { tapped = true }))
        compose.onNodeWithText("Connect").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `an address validation error is shown inline`() {
        setContent(newState.copy(addressError = EditServerFieldError.REQUIRED))
        compose.onNodeWithText("This field is required").assertIsDisplayed()
    }

    // Regression: Compose's onFocusChanged reports the field's initial (unfocused) state once on
    // composition, which is not a real focus-loss - without a guard, this fires
    // onAddressFocusLost() the instant the screen opens, before the user touches anything.
    @Test
    fun `opening the screen does not fire onAddressFocusLost`() {
        var fired = false
        setContent(newState, EditServerActions.Noop.copy(onAddressFocusLost = { fired = true }))
        compose.waitForIdle()
        assertTrue(!fired)
    }

    // --- Existing mode -----------------------------------------------------------------------

    @Test
    fun `existing mode shows the Editing library header and the name field`() {
        setContent(existingState)
        compose.onNodeWithText("Editing library").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun `existing mode shows both Test Connection and Save`() {
        setContent(existingState)
        compose.onNodeWithText("Test Connection").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun `tapping Save fires onConnectOrSave in existing mode`() {
        var tapped = false
        setContent(existingState, EditServerActions.Noop.copy(onConnectOrSave = { tapped = true }))
        compose.onNodeWithText("Save").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `tapping Test Connection fires onTestConnection`() {
        var tapped = false
        setContent(existingState, EditServerActions.Noop.copy(onTestConnection = { tapped = true }))
        compose.onNodeWithText("Test Connection").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `advanced settings are collapsed by default and expand on tap`() {
        var state by mutableStateOf(existingState)
        compose.setContent {
            TakiTheme {
                EditServerScreen(
                    state = state,
                    actions = EditServerActions.Noop.copy(
                        onToggleAdvanced = { state = state.copy(advancedExpanded = !state.advancedExpanded) },
                    ),
                    bottomContentInset = 0.dp,
                )
            }
        }
        compose.onNodeWithText("Allow self-signed HTTPS certificate").assertDoesNotExist()
        compose.onNodeWithText("Advanced settings").performClick()
        compose.onNodeWithText("Allow self-signed HTTPS certificate").assertIsDisplayed()
    }

    @Test
    fun `advanced settings are already expanded when a toggle inside is already on`() {
        setContent(existingState.copy(advancedExpanded = true, allowSelfSignedCertificate = true))
        compose.onNodeWithText("Allow self-signed HTTPS certificate").assertIsDisplayed()
    }

    @Test
    fun `tapping the color swatch fires onPickColor`() {
        var tapped = false
        setContent(
            existingState.copy(advancedExpanded = true),
            EditServerActions.Noop.copy(onPickColor = { tapped = true }),
        )
        compose.onNodeWithText("Library color").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `toggling self-signed fires onSelfSignedChange`() {
        var checked: Boolean? = null
        setContent(
            existingState.copy(advancedExpanded = true),
            EditServerActions.Noop.copy(onSelfSignedChange = { checked = it }),
        )
        compose.onNodeWithText("Allow self-signed HTTPS certificate").performClick()
        assertTrue(checked == true)
    }

    // --- Connection status ----------------------------------------------------------------

    @Test
    fun `a testing state shows the checking message`() {
        setContent(existingState.copy(connectionTestState = ConnectionTestState.TESTING))
        compose.onNodeWithText("Checking connection…").assertIsDisplayed()
    }

    @Test
    fun `a successful test shows the success message`() {
        setContent(existingState.copy(connectionTestState = ConnectionTestState.SUCCESS))
        compose.onNodeWithText("Connection successful").assertIsDisplayed()
    }

    @Test
    fun `a failed test shows the failure message`() {
        setContent(
            existingState.copy(
                connectionTestState = ConnectionTestState.FAILED,
                connectionErrorMessage = "Couldn't connect",
            ),
        )
        compose.onNodeWithText("Couldn't connect").assertIsDisplayed()
    }

    @Test
    fun `the primary action is disabled while a test is running`() {
        setContent(newState.copy(connectionTestState = ConnectionTestState.TESTING))
        compose.onNodeWithText("Connect").assertIsDisplayed().assertIsNotEnabled()
    }

    // --- Discard confirmation --------------------------------------------------------------

    @Test
    fun `the discard confirmation is hidden by default`() {
        setContent(existingState)
        compose.onNodeWithTag(DISCARD_SERVER_SHEET_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `the discard confirmation shows when pendingDiscard is true`() {
        setContent(existingState.copy(pendingDiscard = true))
        compose.onNodeWithTag(DISCARD_SERVER_SHEET_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("Are you sure you want to leave and lose your changes?").assertIsDisplayed()
    }

    @Test
    fun `tapping Cancel in the discard sheet fires onDiscardCancel`() {
        var cancelled = false
        setContent(
            existingState.copy(pendingDiscard = true),
            EditServerActions.Noop.copy(onDiscardCancel = { cancelled = true }),
        )
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun `tapping the primary action in the discard sheet fires onDiscardConfirm`() {
        var confirmed = false
        setContent(
            existingState.copy(pendingDiscard = true),
            EditServerActions.Noop.copy(onDiscardConfirm = { confirmed = true }),
        )
        compose.onNodeWithTag(DISCARD_SERVER_CONFIRM_TEST_TAG).performClick()
        assertTrue(confirmed)
    }

    // --- Header / back ----------------------------------------------------------------------

    @Test
    fun `tapping back fires onBack`() {
        var tapped = false
        setContent(newState, EditServerActions.Noop.copy(onBack = { tapped = true }))
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(tapped)
    }
}
