/*
 * LateSurfacesSweepScreenshotTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.sweep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.collectionlist.CollectionListActions
import org.moire.ultrasonic.ui.collectionlist.CollectionListRow
import org.moire.ultrasonic.ui.collectionlist.CollectionListScreen
import org.moire.ultrasonic.ui.collectionlist.CollectionListUiState
import org.moire.ultrasonic.ui.equalizer.EqualizerActions
import org.moire.ultrasonic.ui.equalizer.EqualizerBandUiState
import org.moire.ultrasonic.ui.equalizer.EqualizerPresetUiState
import org.moire.ultrasonic.ui.equalizer.EqualizerScreen
import org.moire.ultrasonic.ui.equalizer.EqualizerUiState
import org.moire.ultrasonic.ui.serverselector.EditServerActions
import org.moire.ultrasonic.ui.serverselector.EditServerMode
import org.moire.ultrasonic.ui.serverselector.EditServerScreen
import org.moire.ultrasonic.ui.serverselector.EditServerUiState
import org.moire.ultrasonic.ui.settings.ConfirmAction
import org.moire.ultrasonic.ui.settings.SettingsActions
import org.moire.ultrasonic.ui.settings.SettingsDefinitions
import org.moire.ultrasonic.ui.settings.SettingsGroupKeys
import org.moire.ultrasonic.ui.settings.SettingsItem
import org.moire.ultrasonic.ui.settings.SettingsOverlay
import org.moire.ultrasonic.ui.settings.SettingsRowState
import org.moire.ultrasonic.ui.settings.SettingsScreen
import org.moire.ultrasonic.ui.settings.SettingsUiState
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #26 phase 5C.2 responsive sweep: the late surfaces in the harshest combination the app
 * has to survive - Spanish strings, a 320dp-wide phone and a 1.3x font scale. If nothing clips
 * here it holds for the individual cases too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-w320dp-h1200dp-xxhdpi")
class LateSurfacesSweepScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(tag: String, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = FONT_SCALE),
            ) {
                TakiTheme {
                    Box(
                        modifier = Modifier
                            .testTag(tag)
                            .width(320.dp)
                            .heightIn(max = 1200.dp)
                            .background(TakiTheme.colors.black),
                    ) { content() }
                }
            }
        }
        compose.onNodeWithTag(tag).captureRoboImage("src/test/screenshots/$tag.png")
    }

    private fun rowState(item: SettingsItem): SettingsRowState = when (item) {
        is SettingsItem.Toggle -> SettingsRowState.ToggleRow(item, checked = false)
        is SettingsItem.Choice -> SettingsRowState.ChoiceRow(item, currentValue = item.defaultValue)
        is SettingsItem.Navigation -> SettingsRowState.NavigationRow(item)
        is SettingsItem.Action -> SettingsRowState.ActionRow(item)
        is SettingsItem.Category -> SettingsRowState.CategoryRow(item)
    }

    private val advanced: SettingsUiState = SettingsDefinitions.forRootKey(SettingsGroupKeys.ADVANCED)
        .let { (title, items) -> SettingsUiState(titleRes = title, rows = items.map { rowState(it) }) }

    @Test
    fun settingsAdvanced() = capture("sweep_es_settings_advanced") {
        SettingsScreen(advanced, SettingsActions.Noop, 0.dp)
    }

    @Test
    fun settingsConfirm() = capture("sweep_es_settings_confirm") {
        val overlay = SettingsOverlay.Confirm(
            titleRes = R.string.common_confirm,
            messageRes = R.string.settings_clear_image_cache_confirm,
            confirmLabelRes = R.string.common_ok,
            dismissLabelRes = R.string.common_cancel,
            action = ConfirmAction.CLEAR_IMAGE_CACHE,
        )
        SettingsScreen(advanced.copy(overlay = overlay), SettingsActions.Noop, 0.dp)
    }

    @Test
    fun equalizer() = capture("sweep_es_equalizer") {
        val bands = listOf(30 to 120, 120 to 460, 460 to 1800, 1800 to 7000, 7000 to 20000)
            .mapIndexed { i, (lo, hi) -> EqualizerBandUiState(i, lo, hi, -1500, 1500, if (i == 0) 300 else 0) }
        val state = EqualizerUiState(
            controllerAvailable = true,
            enabled = true,
            bands = bands,
            presets = listOf(EqualizerPresetUiState(0, "Normal")),
            currentPresetIndex = 0,
        )
        EqualizerScreen(state, EqualizerActions.Noop, 0.dp)
    }

    @Test
    fun editServer() = capture("sweep_es_edit_server") {
        val state = EditServerUiState(
            mode = EditServerMode.Existing(1),
            name = "Home Server",
            address = "https://home.example.com",
            username = "joseph",
            password = "secret",
            advancedExpanded = true,
        )
        EditServerScreen(state, EditServerActions.Noop, 0.dp)
    }

    @Test
    fun boxSets() = capture("sweep_es_box_sets") {
        val rows = persistentListOf(
            CollectionListRow("collection:a", "The Complete Mozart Edition", 2),
            CollectionListRow("collection:b", "Bach 333", 222),
        )
        CollectionListScreen(CollectionListUiState(isLoading = false, rows = rows), CollectionListActions.Noop, 0.dp)
    }

    private companion object {
        const val FONT_SCALE = 1.3f
    }
}
