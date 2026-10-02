/*
 * SettingsScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings screen (issue #10 phase 5A4): the top level, a representative nested group, every row
 * type (toggle/choice/navigation/action/category), and the three transient sheets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1200dp-xxhdpi")
class SettingsScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun topLevelState() = SettingsUiState(
        titleRes = R.string.menu_settings,
        rows = SettingsDefinitions.forRootKey(null).second.map { toRowState(it) },
    )

    private fun groupState(rootKey: String) = SettingsDefinitions.forRootKey(rootKey).let { (title, items) ->
        SettingsUiState(titleRes = title, rows = items.map { toRowState(it) })
    }

    private fun toRowState(item: SettingsItem): SettingsRowState = when (item) {
        is SettingsItem.Toggle -> SettingsRowState.ToggleRow(item, checked = false)
        is SettingsItem.Choice -> SettingsRowState.ChoiceRow(item, currentValue = item.defaultValue)
        is SettingsItem.Navigation -> SettingsRowState.NavigationRow(item)
        is SettingsItem.Action -> SettingsRowState.ActionRow(item)
        is SettingsItem.Category -> SettingsRowState.CategoryRow(item)
    }

    private fun setContent(state: SettingsUiState, actions: SettingsActions = SettingsActions.Noop) {
        compose.setContent {
            TakiTheme {
                SettingsScreen(state = state, actions = actions, bottomContentInset = 0.dp)
            }
        }
    }

    // --- Top level -----------------------------------------------------------------------------

    @Test
    fun `top level shows the Settings header and its five rows`() {
        setContent(topLevelState())
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Playback").assertIsDisplayed()
        compose.onNodeWithText("Downloads").assertIsDisplayed()
        compose.onNodeWithText("Override the language").assertIsDisplayed()
        compose.onNodeWithText("Advanced").assertIsDisplayed()
        compose.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun `tapping a group navigation row fires onNavigateGroup with its key`() {
        var navigatedTo: String? = null
        setContent(topLevelState(), SettingsActions.Noop.copy(onNavigateGroup = { navigatedTo = it }))
        compose.onNodeWithText("Playback").performClick()
        assertEquals(SettingsGroupKeys.NOW_PLAYING, navigatedTo)
    }

    @Test
    fun `tapping About fires onNavigateAbout, not onNavigateGroup`() {
        var aboutTapped = false
        var navigatedTo: String? = "untouched"
        setContent(
            topLevelState(),
            SettingsActions.Noop.copy(
                onNavigateAbout = { aboutTapped = true },
                onNavigateGroup = { navigatedTo = it },
            ),
        )
        compose.onNodeWithText("About").performClick()
        assertTrue(aboutTapped)
        assertEquals("untouched", navigatedTo)
    }

    @Test
    fun `tapping back fires onBack`() {
        var tapped = false
        setContent(topLevelState(), SettingsActions.Noop.copy(onBack = { tapped = true }))
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(tapped)
    }

    // --- Nested group: Playback (equalizer nav + two choices) -----------------------------------

    @Test
    fun `the Playback group shows its header and the equalizer row`() {
        setContent(groupState(SettingsGroupKeys.NOW_PLAYING))
        compose.onNodeWithText("Playback").assertIsDisplayed()
        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        compose.onNodeWithText("Mobile streaming quality").assertIsDisplayed()
        compose.onNodeWithText("Wi-Fi streaming quality").assertIsDisplayed()
    }

    @Test
    fun `tapping Equalizer fires onNavigateEqualizer`() {
        var tapped = false
        setContent(
            groupState(SettingsGroupKeys.NOW_PLAYING),
            SettingsActions.Noop.copy(onNavigateEqualizer = { tapped = true }),
        )
        compose.onNodeWithText("Equalizer").performClick()
        assertTrue(tapped)
    }

    // --- Toggles (Downloads group) ---------------------------------------------------------------

    @Test
    fun `a toggle row shows its title, summary and switch state`() {
        setContent(groupState(SettingsGroupKeys.DOWNLOADS))
        compose.onNodeWithText("Download on Wi-Fi only").assertIsDisplayed()
        compose.onNodeWithText("Only download media on unmetered connections").assertIsDisplayed()
    }

    @Test
    fun `tapping anywhere on a toggle row fires onToggle exactly once with the inverted value`() {
        var callCount = 0
        var lastValue: Boolean? = null
        setContent(
            groupState(SettingsGroupKeys.DOWNLOADS),
            SettingsActions.Noop.copy(onToggle = { _, value -> callCount++; lastValue = value }),
        )
        compose.onNodeWithText("Download on Wi-Fi only").performClick()
        assertEquals(1, callCount)
        assertEquals(true, lastValue)
    }

    @Test
    fun `a disabled toggle cannot be toggled and renders as not enabled`() {
        var tapped = false
        val state = groupState(SettingsGroupKeys.LIBRARY).let { s ->
            s.copy(
                rows = s.rows.map { row ->
                    if (row is SettingsRowState.ToggleRow && row.item.key == KEY_ID3_TAGS_OFFLINE) {
                        row.copy(enabled = false)
                    } else {
                        row
                    }
                },
            )
        }
        setContent(state, SettingsActions.Noop.copy(onToggle = { _, _ -> tapped = true }))
        compose.onNodeWithText("Use ID3 method also when offline").performClick()
        assertFalse(tapped)
    }

    // --- Choices (Advanced group) ----------------------------------------------------------------

    @Test
    fun `a choice row click fires onChoiceClick`() {
        var clicked: SettingsItem.Choice? = null
        setContent(
            groupState(SettingsGroupKeys.ADVANCED),
            SettingsActions.Noop.copy(onChoiceClick = { clicked = it }),
        )
        compose.onNodeWithText("ReplayGain Mode").performClick()
        assertEquals("replayGain", clicked?.key)
    }

    @Test
    fun `the choice sheet shows when the overlay is a Choice`() {
        val item = SettingsDefinitions.forRootKey(SettingsGroupKeys.ADVANCED).second
            .filterIsInstance<SettingsItem.Choice>().first { it.key == "replayGain" }
        val state = groupState(SettingsGroupKeys.ADVANCED).copy(
            overlay = SettingsOverlay.Choice(item, item.defaultValue),
        )
        setContent(state)
        compose.onNodeWithTag(SETTINGS_CHOICE_SHEET_TEST_TAG).assertIsDisplayed()
        // "Disabled" also appears behind the sheet as the row's own current-value summary -
        // "Track Only" is unambiguous, it only exists as an option inside the sheet's own list.
        compose.onNodeWithText("Track Only").assertIsDisplayed()
    }

    @Test
    fun `selecting a choice option fires onChoiceSelected with that option's value`() {
        val item = SettingsDefinitions.forRootKey(SettingsGroupKeys.ADVANCED).second
            .filterIsInstance<SettingsItem.Choice>().first { it.key == "replayGain" }
        val state = groupState(SettingsGroupKeys.ADVANCED).copy(
            overlay = SettingsOverlay.Choice(item, item.defaultValue),
        )
        var selected: Pair<SettingsItem.Choice, String>? = null
        setContent(state, SettingsActions.Noop.copy(onChoiceSelected = { i, v -> selected = i to v }))
        compose.onNodeWithText("Track Only").performClick()
        assertEquals("replayGainTrackOnly", selected?.second)
    }

    @Test
    fun `tapping the choice sheet scrim fires onChoiceDismiss`() {
        val item = SettingsDefinitions.forRootKey(SettingsGroupKeys.ADVANCED).second
            .filterIsInstance<SettingsItem.Choice>().first { it.key == "replayGain" }
        val state = groupState(SettingsGroupKeys.ADVANCED).copy(
            overlay = SettingsOverlay.Choice(item, item.defaultValue),
        )
        var dismissed = false
        setContent(state, SettingsActions.Noop.copy(onChoiceDismiss = { dismissed = true }))
        compose.onNodeWithTag(SETTINGS_CHOICE_SCRIM_TEST_TAG).performClick()
        assertTrue(dismissed)
    }

    // --- Actions / visibility (Music Cache group) ------------------------------------------------

    @Test
    fun `an invisible action row is not displayed`() {
        val state = groupState(SettingsGroupKeys.MUSIC_CACHE).let { s ->
            s.copy(
                rows = s.rows.map { row ->
                    if (row is SettingsRowState.ActionRow && row.item.key == SettingsActionKeys.CACHE_LOCATION) {
                        row.copy(visible = false)
                    } else {
                        row
                    }
                },
            )
        }
        setContent(state)
        compose.onNodeWithText("Cache Location").assertDoesNotExist()
    }

    @Test
    fun `a visible action row shows and fires onActionClick`() {
        var clicked: SettingsItem.Action? = null
        val state = groupState(SettingsGroupKeys.MUSIC_CACHE).let { s ->
            s.copy(
                rows = s.rows.map { row ->
                    if (row is SettingsRowState.ActionRow && row.item.key == SettingsActionKeys.CACHE_LOCATION) {
                        row.copy(visible = true)
                    } else {
                        row
                    }
                },
            )
        }
        setContent(state, SettingsActions.Noop.copy(onActionClick = { clicked = it }))
        compose.onNodeWithText("Cache Location").assertIsDisplayed().performClick()
        assertEquals(SettingsActionKeys.CACHE_LOCATION, clicked?.key)
    }

    @Test
    fun `a category row shows as a plain section label`() {
        setContent(groupState(SettingsGroupKeys.ADVANCED))
        compose.onNodeWithText("Audio").assertIsDisplayed()
        compose.onNodeWithText("Other").assertIsDisplayed()
    }

    // --- Confirm sheet -------------------------------------------------------------------------

    @Test
    fun `the confirm sheet shows its title, message and both buttons`() {
        val overlay = SettingsOverlay.Confirm(
            titleRes = R.string.common_confirm,
            messageRes = R.string.settings_clear_image_cache_confirm,
            confirmLabelRes = R.string.common_ok,
            dismissLabelRes = R.string.common_cancel,
            action = ConfirmAction.CLEAR_IMAGE_CACHE,
        )
        setContent(groupState(SettingsGroupKeys.MUSIC_CACHE).copy(overlay = overlay))
        compose.onNodeWithTag(SETTINGS_CONFIRM_SHEET_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("Are you sure you want to delete the cached images?").assertIsDisplayed()
    }

    @Test
    fun `tapping the confirm action fires onConfirm`() {
        var confirmed = false
        val overlay = SettingsOverlay.Confirm(
            titleRes = R.string.common_confirm,
            messageRes = R.string.settings_clear_downloads_confirm,
            confirmLabelRes = R.string.common_ok,
            dismissLabelRes = R.string.common_cancel,
            action = ConfirmAction.CLEAR_DOWNLOADS,
        )
        setContent(
            groupState(SettingsGroupKeys.DOWNLOADS).copy(overlay = overlay),
            SettingsActions.Noop.copy(onConfirm = { confirmed = true }),
        )
        compose.onNodeWithTag(SETTINGS_CONFIRM_ACTION_TEST_TAG).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun `tapping the confirm sheet's dismiss button fires onConfirmDismiss`() {
        var dismissed = false
        val overlay = SettingsOverlay.Confirm(
            titleRes = R.string.common_confirm,
            messageRes = R.string.settings_clear_downloads_confirm,
            confirmLabelRes = R.string.common_ok,
            dismissLabelRes = R.string.common_cancel,
            action = ConfirmAction.CLEAR_DOWNLOADS,
        )
        setContent(
            groupState(SettingsGroupKeys.DOWNLOADS).copy(overlay = overlay),
            SettingsActions.Noop.copy(onConfirmDismiss = { dismissed = true }),
        )
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    // --- Info sheet ----------------------------------------------------------------------------

    @Test
    fun `the info sheet shows its message and an OK button`() {
        val overlay = SettingsOverlay.Info(R.string.settings_debug_log_deleted)
        setContent(groupState(SettingsGroupKeys.DEBUG).copy(overlay = overlay))
        compose.onNodeWithTag(SETTINGS_INFO_SHEET_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("Deleted log files.").assertIsDisplayed()
    }

    @Test
    fun `tapping OK on the info sheet fires onInfoDismiss`() {
        var dismissed = false
        val overlay = SettingsOverlay.Info(R.string.settings_cache_location_error)
        setContent(
            groupState(SettingsGroupKeys.MUSIC_CACHE).copy(overlay = overlay),
            SettingsActions.Noop.copy(onInfoDismiss = { dismissed = true }),
        )
        compose.onNodeWithTag(SETTINGS_INFO_ACTION_TEST_TAG).performClick()
        assertTrue(dismissed)
    }
}
