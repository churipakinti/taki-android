/*
 * EqualizerScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.equalizer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Equalizer screen (issue #10 phase 5A5): header, the unavailable state, the master switch, one
 * slider per runtime band, the preset row + sheet, accessibility, and the bottom inset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h500dp-xxhdpi")
class EqualizerScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val ranges = listOf(30 to 120, 120 to 460, 460 to 1800, 1800 to 7000, 7000 to 20000)

    private fun band(index: Int, level: Int = 0) = EqualizerBandUiState(
        index = index,
        lowFrequencyHz = ranges[index].first,
        highFrequencyHz = ranges[index].second,
        minLevel = -1500,
        maxLevel = 1500,
        level = level,
    )

    private fun available(
        enabled: Boolean = true,
        levels: List<Int> = listOf(300, 0, -500, 0, 300),
        presets: List<String> = listOf("Normal", "Classical", "Dance", "Flat", "Folk"),
        current: Int? = null,
        sheet: Boolean = false,
    ) = EqualizerUiState(
        controllerAvailable = true,
        enabled = enabled,
        bands = levels.mapIndexed { i, l -> band(i, l) },
        presets = presets.mapIndexed { i, n -> EqualizerPresetUiState(i, n) },
        currentPresetIndex = current,
        presetSheetVisible = sheet,
    )

    private fun setContent(
        state: EqualizerUiState,
        actions: EqualizerActions = EqualizerActions.Noop,
        inset: Dp = 0.dp,
    ) {
        compose.setContent {
            TakiTheme { EqualizerScreen(state = state, actions = actions, bottomContentInset = inset) }
        }
    }

    // --- Header / unavailable ------------------------------------------------------------------

    @Test
    fun `shows the Equalizer header and back fires onBack`() {
        var tapped = false
        setContent(available(), EqualizerActions.Noop.copy(onBack = { tapped = true }))

        compose.onNodeWithText("Equalizer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go back").performClick()
        assertTrue(tapped)
    }

    @Test
    fun `the unavailable state explains itself and shows no controls`() {
        setContent(EqualizerUiState())

        compose.onNodeWithTag(EQUALIZER_UNAVAILABLE_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("Equalizer unavailable").assertIsDisplayed()
        compose.onNodeWithTag(EQUALIZER_ENABLED_ROW_TEST_TAG).assertDoesNotExist()
        compose.onNodeWithTag(equalizerBandTestTag(0)).assertDoesNotExist()
        compose.onNodeWithTag(EQUALIZER_PRESET_ROW_TEST_TAG).assertDoesNotExist()
    }

    // --- Bands: labels, dynamic count ----------------------------------------------------------

    @Test
    fun `every runtime band shows its frequency range and dB level`() {
        setContent(available())

        compose.onNodeWithText("30 - 120 Hz", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("120 - 460 Hz", useUnmergedTree = true).assertIsDisplayed()
        // Bands 0 and 4 are both +3 dB; band 2 is -5 dB.
        compose.onAllNodesWithText("+3 dB", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("-5 dB", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `the number of sliders follows the number of bands`() {
        setContent(available(levels = listOf(0, 0, 0)))

        (0..2).forEach { compose.onNodeWithTag(equalizerBandTestTag(it)).assertExists() }
        compose.onNodeWithTag(equalizerBandTestTag(3)).assertDoesNotExist()
    }

    @Test
    fun `a band slider spans the runtime's millibel range at the band's level`() {
        setContent(available())

        compose.onNodeWithTag(equalizerBandTestTag(0))
            .assertRangeInfoEquals(ProgressBarRangeInfo(300f, -1500f..1500f))
    }

    @Test
    fun `moving a slider reports the band and the millibel level`() {
        var reported: Pair<Int, Int>? = null
        setContent(
            available(),
            EqualizerActions.Noop.copy(onBandLevelChange = { band, level -> reported = band to level }),
        )

        compose.onNodeWithTag(equalizerBandTestTag(2))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(750f) }

        assertEquals(2 to 750, reported)
    }

    @Test
    fun `merely rendering never reports a band change`() {
        var calls = 0
        setContent(available(), EqualizerActions.Noop.copy(onBandLevelChange = { _, _ -> calls++ }))
        compose.waitForIdle()
        assertEquals(0, calls)
    }

    // --- Master switch -------------------------------------------------------------------------

    @Test
    fun `tapping the enabled row fires onEnabledChange once with the inverted value`() {
        val calls = mutableListOf<Boolean>()
        setContent(available(enabled = false), EqualizerActions.Noop.copy(onEnabledChange = { calls += it }))

        compose.onNodeWithTag(EQUALIZER_ENABLED_ROW_TEST_TAG).performClick()

        assertEquals(listOf(true), calls)
    }

    @Test
    fun `the enabled row exposes its checked state`() {
        setContent(available(enabled = true))
        compose.onNodeWithTag(EQUALIZER_ENABLED_ROW_TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `bands are enabled while the equalizer is on and disabled while it is off`() {
        setContent(available(enabled = true))
        (0..4).forEach { compose.onNodeWithTag(equalizerBandTestTag(it)).assertIsEnabled() }
    }

    @Test
    fun `every band slider is disabled while the equalizer is off`() {
        setContent(available(enabled = false))
        (0..4).forEach { compose.onNodeWithTag(equalizerBandTestTag(it)).assertIsNotEnabled() }
    }

    // --- Accessibility ------------------------------------------------------------------------

    @Test
    fun `a band slider reads as one control - range, level and bounds`() {
        setContent(available())

        compose.onNodeWithTag(equalizerBandTestTag(0)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("30 to 120 Hz")),
        )
        compose.onNodeWithTag(equalizerBandTestTag(0)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "+3 dB"),
        )
    }

    @Test
    fun `the two toggle rows meet the 48dp touch target`() {
        setContent(available())

        listOf(EQUALIZER_ENABLED_ROW_TEST_TAG, EQUALIZER_PRESET_ROW_TEST_TAG).forEach { tag ->
            val height = compose.onNodeWithTag(tag).getBoundsInRoot().height
            assertTrue("$tag is only $height tall", height >= 48.dp)
        }
    }

    @Test
    fun `a band slider is draggable within the 48dp target around its 44dp track area`() {
        var reported = 0
        setContent(
            available(),
            EqualizerActions.Noop.copy(onBandLevelChange = { _, _ -> reported++ }),
        )
        val slider = compose.onNodeWithTag(equalizerBandTestTag(0))

        // The slider's own bounds are 44dp tall; Compose hit-tests every pointer target at a
        // minimum of 48dp, so a touch 2dp above its top edge must still land on it.
        slider.performTouchInput { click(Offset(width * 0.25f, -2.dp.toPx())) }

        assertTrue("a touch just outside the 44dp bounds must still move the slider", reported > 0)

        // ...but the expansion is only to 48dp: a touch well outside must not.
        reported = 0
        slider.performTouchInput { click(Offset(width * 0.25f, -10.dp.toPx())) }
        assertEquals(0, reported)
    }

    // --- Presets -------------------------------------------------------------------------------

    @Test
    fun `the preset row shows the current preset name when the runtime reports one`() {
        setContent(available(current = 3))
        compose.onNodeWithText("Flat").assertIsDisplayed()
    }

    @Test
    fun `the preset row shows no name when the runtime reports no preset`() {
        setContent(available(current = null))
        compose.onNodeWithText("Flat").assertDoesNotExist()
        compose.onNodeWithText("Select Preset").assertIsDisplayed()
    }

    @Test
    fun `tapping the preset row fires onPresetClick`() {
        var tapped = false
        setContent(available(), EqualizerActions.Noop.copy(onPresetClick = { tapped = true }))
        compose.onNodeWithTag(EQUALIZER_PRESET_ROW_TEST_TAG).performClick()
        assertTrue(tapped)
    }

    @Test
    fun `the preset row is disabled when the runtime has no presets`() {
        setContent(available(presets = emptyList()))
        compose.onNodeWithTag(EQUALIZER_PRESET_ROW_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the preset sheet lists every runtime preset and selects only the current one`() {
        setContent(available(current = 1, sheet = true))

        compose.onNodeWithTag(EQUALIZER_PRESET_SHEET_TEST_TAG).assertIsDisplayed()
        listOf("Normal", "Classical", "Dance", "Flat", "Folk").forEachIndexed { i, _ ->
            compose.onNodeWithTag(equalizerPresetTestTag(i)).assertExists()
        }
        compose.onNodeWithTag(equalizerPresetTestTag(1)).assertIsSelected()
        compose.onNodeWithTag(equalizerPresetTestTag(0)).assertIsNotSelected()
    }

    // Regression from the Pixel 7: a fixed 420dp list cap hid the 10th of the device's presets.
    @Test
    @Config(qualifiers = "w412dp-h900dp-xxhdpi")
    fun `on a phone-sized screen all ten runtime presets are visible without scrolling`() {
        val names = List(10) { "Preset $it" }
        setContent(available(presets = names, sheet = true))

        (0..9).forEach { compose.onNodeWithTag(equalizerPresetTestTag(it)).assertIsDisplayed() }
    }

    @Test
    fun `on a small screen every preset is still reachable by scrolling the sheet`() {
        val names = List(14) { "Preset $it" }
        setContent(available(presets = names, sheet = true))

        compose.onNodeWithTag(EQUALIZER_PRESET_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(equalizerPresetTestTag(13)))
        compose.onNodeWithTag(equalizerPresetTestTag(13)).assertIsDisplayed()
    }

    @Test
    fun `with no current preset nothing in the sheet is selected`() {
        setContent(available(current = null, sheet = true))
        (0..4).forEach { compose.onNodeWithTag(equalizerPresetTestTag(it)).assertIsNotSelected() }
    }

    @Test
    fun `choosing a preset reports its index`() {
        var chosen: Int? = null
        setContent(
            available(sheet = true),
            EqualizerActions.Noop.copy(onPresetSelected = { chosen = it }),
        )
        compose.onNodeWithTag(equalizerPresetTestTag(2)).performClick()
        assertEquals(2, chosen)
    }

    @Test
    fun `tapping the scrim dismisses the preset sheet`() {
        var dismissed = false
        setContent(
            available(sheet = true),
            EqualizerActions.Noop.copy(onPresetSheetDismiss = { dismissed = true }),
        )
        // Top of the scrim: its centre is under the panel (which now swallows taps, #28).
        compose.onNodeWithTag(EQUALIZER_PRESET_SCRIM_TEST_TAG).performTouchInput {
            click(topCenter + androidx.compose.ui.geometry.Offset(0f, 40f))
        }
        assertTrue(dismissed)
    }

    // --- Bottom inset --------------------------------------------------------------------------

    @Test
    fun `the last band can scroll clear of a non-zero bottom inset`() {
        val inset = 120.dp
        setContent(available(), inset = inset)

        // items: enabled, preset, bands header, 5 bands, bottom inset spacer => last index 8
        compose.onNodeWithTag(EQUALIZER_CONTENT_TEST_TAG).performScrollToIndex(8)

        val rootBottom = compose.onRoot().getBoundsInRoot().bottom
        val lastBandBottom = compose.onNodeWithTag(equalizerBandTestTag(4)).getBoundsInRoot().bottom
        assertTrue(
            "last band ($lastBandBottom) must end above the floating chrome (${rootBottom - inset})",
            lastBandBottom <= rootBottom - inset,
        )
    }

    @Test
    fun `an empty-state screen is also lifted by the inset`() {
        setContent(EqualizerUiState(), inset = 120.dp)
        compose.onNodeWithTag(EQUALIZER_UNAVAILABLE_TEST_TAG).assertIsDisplayed()
        compose.onNode(hasTestTag(EQUALIZER_UNAVAILABLE_TEST_TAG)).getBoundsInRoot().let {
            assertTrue(it.bottom <= compose.onRoot().getBoundsInRoot().bottom - 120.dp)
        }
    }
}
