/*
 * SettingsScreenScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi goldens for Settings (issue #10 phase 5A4): the top level, one representative nested
 * group (Advanced - the only screen exercising every row type: toggle, choice, navigation, and
 * category), and the choice sheet. Fully deterministic - fake [SettingsUiState], no real
 * SharedPreferences. Record with:
 *   ./gradlew :ultrasonic:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h1200dp-xxhdpi")
class SettingsScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun toRowState(item: SettingsItem): SettingsRowState = when (item) {
        is SettingsItem.Toggle -> SettingsRowState.ToggleRow(item, checked = false)
        is SettingsItem.Choice -> SettingsRowState.ChoiceRow(item, currentValue = item.defaultValue)
        is SettingsItem.Navigation -> SettingsRowState.NavigationRow(item)
        is SettingsItem.Action -> SettingsRowState.ActionRow(item)
        is SettingsItem.Category -> SettingsRowState.CategoryRow(item)
    }

    private fun groupState(rootKey: String?): SettingsUiState {
        val (title, items) = SettingsDefinitions.forRootKey(rootKey)
        return SettingsUiState(titleRes = title, rows = items.map { toRowState(it) })
    }

    private val topLevel = groupState(null)
    private val advanced = groupState(SettingsGroupKeys.ADVANCED)

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            TakiTheme {
                Box(
                    modifier = Modifier
                        .testTag(tag)
                        .width(420.dp)
                        .heightIn(max = 1200.dp)
                        .background(TakiTheme.colors.black),
                ) {
                    content()
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun screen(state: SettingsUiState): @Composable () -> Unit =
        { SettingsScreen(state = state, actions = SettingsActions.Noop, bottomContentInset = 0.dp) }

    @Test
    fun settingsTopLevel() = capture("settings_top_level") { screen(topLevel)() }

    @Test
    fun settingsAdvancedGroup() = capture("settings_advanced_group") { screen(advanced)() }

    @Test
    fun settingsChoiceSheet() = capture("settings_choice_sheet_golden") {
        val item = advanced.rows.filterIsInstance<SettingsRowState.ChoiceRow>()
            .first { it.item.key == "replayGain" }.item
        screen(advanced.copy(overlay = SettingsOverlay.Choice(item, item.defaultValue)))()
    }
}
